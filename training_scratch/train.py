import argparse,json,math,random,time
from pathlib import Path
import numpy as np
import sentencepiece as spm
import torch
from torch.utils.data import Dataset,DataLoader
from model import BanglaGPT
class Blocks(Dataset):
    def __init__(self,ids,seq): self.ids,self.seq=ids,seq
    def __len__(self): return max(0,(len(self.ids)-1)//self.seq)
    def __getitem__(self,i):
        s=i*self.seq
        return torch.tensor(self.ids[s:s+self.seq]),torch.tensor(self.ids[s+1:s+self.seq+1])
p=argparse.ArgumentParser()
p.add_argument("--corpus",default="training_scratch/data/bengali_corpus.txt")
p.add_argument("--tokenizer",default="output/bangla_tokenizer.model")
p.add_argument("--out",default="output/bangla-from-scratch")
p.add_argument("--steps",type=int,default=100000); p.add_argument("--batch",type=int,default=8)
p.add_argument("--seq",type=int,default=512); p.add_argument("--dim",type=int,default=512)
p.add_argument("--layers",type=int,default=8); p.add_argument("--heads",type=int,default=8)
p.add_argument("--lr",type=float,default=3e-4); p.add_argument("--seed",type=int,default=20261115)
a=p.parse_args()
random.seed(a.seed); np.random.seed(a.seed); torch.manual_seed(a.seed)
corpus=Path(a.corpus)
if not corpus.exists() or corpus.stat().st_size<10_000_000:
    raise SystemExit("Corpus missing/too small: provide at least 10 MB of licensed Bengali text.")
sp=spm.SentencePieceProcessor(model_file=a.tokenizer)
ids=sp.encode(corpus.read_text(encoding="utf-8"),out_type=int)
if len(ids)<2_000_000: raise SystemExit("At least 2 million tokens required for this starter training run.")
cut=int(len(ids)*0.99)
tr,va=Blocks(ids[:cut],a.seq),Blocks(ids[cut:],a.seq)
if not len(tr) or not len(va): raise SystemExit("Corpus too small for train/validation split.")
if not torch.cuda.is_available(): raise SystemExit("CUDA GPU required; standard GitHub-hosted runners do not provide one.")
device="cuda"; torch.backends.cuda.matmul.allow_tf32=True
train=DataLoader(tr,batch_size=a.batch,shuffle=True,drop_last=True,num_workers=2,pin_memory=True)
valid=DataLoader(va,batch_size=a.batch,shuffle=False,num_workers=2,pin_memory=True)
model=BanglaGPT(sp.get_piece_size(),a.seq,a.dim,a.layers,a.heads).to(device)
opt=torch.optim.AdamW(model.parameters(),lr=a.lr,betas=(0.9,0.95),weight_decay=0.1)
scaler=torch.amp.GradScaler("cuda")
out=Path(a.out); out.mkdir(parents=True,exist_ok=True)
print(json.dumps({"device":device,"parameters":sum(x.numel() for x in model.parameters()),"tokens":len(ids)}))
def evaluate():
    model.eval(); losses=[]
    with torch.no_grad():
        for i,(x,y) in enumerate(valid):
            if i>=100: break
            with torch.autocast("cuda",dtype=torch.bfloat16): _,loss=model(x.to(device),y.to(device))
            losses.append(loss.item())
    model.train()
    return float(np.mean(losses))
step=0; started=time.time(); model.train()
while step<a.steps:
    for x,y in train:
        step+=1
        x=x.to(device,non_blocking=True); y=y.to(device,non_blocking=True)
        lr=a.lr*0.5*(1+math.cos(math.pi*min(step,a.steps)/a.steps))
        for g in opt.param_groups: g["lr"]=max(lr,a.lr*0.05)
        opt.zero_grad(set_to_none=True)
        with torch.autocast("cuda",dtype=torch.bfloat16): _,loss=model(x,y)
        scaler.scale(loss).backward(); scaler.unscale_(opt)
        torch.nn.utils.clip_grad_norm_(model.parameters(),1.0)
        scaler.step(opt); scaler.update()
        if step%100==0: print(json.dumps({"step":step,"loss":loss.item(),"seconds":int(time.time()-started)}),flush=True)
        if step%2000==0 or step==a.steps:
            vl=evaluate()
            torch.save({"step":step,"model":model.state_dict(),"config":vars(a),"val_loss":vl},out/"checkpoint.pt")
            with (out/"metrics.jsonl").open("a") as f: f.write(json.dumps({"step":step,"val_loss":vl})+"\n")
            print(json.dumps({"step":step,"validation_loss":vl}),flush=True)
        if step>=a.steps: break
torch.save({"step":step,"model":model.state_dict(),"config":vars(a)},out/"final.pt")
sp.save(out/"tokenizer.model")
print("Training complete")
