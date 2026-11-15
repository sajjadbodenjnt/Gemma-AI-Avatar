import math
import torch
from torch import nn
from torch.nn import functional as F

class CausalSelfAttention(nn.Module):
    def __init__(self, dim, heads, dropout):
        super().__init__()
        assert dim % heads == 0
        self.heads, self.head_dim = heads, dim // heads
        self.qkv = nn.Linear(dim, 3 * dim, bias=False)
        self.proj = nn.Linear(dim, dim, bias=False)
        self.dropout = dropout
    def forward(self, x):
        b,t,c=x.shape
        q,k,v=self.qkv(x).chunk(3,dim=-1)
        shape=(b,t,self.heads,self.head_dim)
        q=q.view(shape).transpose(1,2); k=k.view(shape).transpose(1,2); v=v.view(shape).transpose(1,2)
        y=F.scaled_dot_product_attention(q,k,v,is_causal=True,dropout_p=self.dropout if self.training else 0.0)
        return self.proj(y.transpose(1,2).contiguous().view(b,t,c))

class Block(nn.Module):
    def __init__(self,dim,heads,dropout):
        super().__init__()
        self.n1=nn.LayerNorm(dim); self.n2=nn.LayerNorm(dim)
        self.attn=CausalSelfAttention(dim,heads,dropout)
        self.mlp=nn.Sequential(nn.Linear(dim,4*dim),nn.GELU(),nn.Linear(4*dim,dim),nn.Dropout(dropout))
    def forward(self,x):
        x=x+self.attn(self.n1(x))
        return x+self.mlp(self.n2(x))

class BanglaGPT(nn.Module):
    """Decoder-only Transformer with random initialization; no pretrained weights."""
    def __init__(self,vocab_size,seq_len=512,dim=512,layers=8,heads=8,dropout=0.1):
        super().__init__()
        self.seq_len=seq_len
        self.token=nn.Embedding(vocab_size,dim)
        self.position=nn.Embedding(seq_len,dim)
        self.drop=nn.Dropout(dropout)
        self.blocks=nn.ModuleList([Block(dim,heads,dropout) for _ in range(layers)])
        self.norm=nn.LayerNorm(dim)
        self.head=nn.Linear(dim,vocab_size,bias=False)
        self.head.weight=self.token.weight
        self.apply(self._init)
        for name,p in self.named_parameters():
            if name.endswith("proj.weight") or name.endswith("mlp.2.weight"):
                nn.init.normal_(p,mean=0.0,std=0.02/math.sqrt(2*layers))
    @staticmethod
    def _init(m):
        if isinstance(m,nn.Linear):
            nn.init.normal_(m.weight,mean=0.0,std=0.02)
            if m.bias is not None: nn.init.zeros_(m.bias)
        elif isinstance(m,nn.Embedding): nn.init.normal_(m.weight,mean=0.0,std=0.02)
    def forward(self,ids,targets=None):
        b,t=ids.shape
        if t>self.seq_len: raise ValueError("Sequence exceeds configured context")
        pos=torch.arange(t,device=ids.device)
        x=self.drop(self.token(ids)+self.position(pos)[None,:,:])
        for block in self.blocks: x=block(x)
        logits=self.head(self.norm(x))
        loss=F.cross_entropy(logits.reshape(-1,logits.size(-1)),targets.reshape(-1)) if targets is not None else None
        return logits,loss
