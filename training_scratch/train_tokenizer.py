import argparse
from pathlib import Path
import sentencepiece as spm
p=argparse.ArgumentParser()
p.add_argument("--input",default="training_scratch/data/bengali_corpus.txt")
p.add_argument("--output",default="output/bangla_tokenizer")
p.add_argument("--vocab-size",type=int,default=16000)
a=p.parse_args()
src=Path(a.input)
if not src.exists() or src.stat().st_size<10_000_000:
    raise SystemExit("Supply a licensed UTF-8 Bengali corpus of at least 10 MB.")
Path(a.output).parent.mkdir(parents=True,exist_ok=True)
spm.SentencePieceTrainer.train(input=str(src),model_prefix=a.output,vocab_size=a.vocab_size,
    model_type="bpe",character_coverage=0.9999,pad_id=0,unk_id=1,bos_id=2,eos_id=3,
    byte_fallback=True,split_digits=True,normalization_rule_name="nmt_nfkc")
print("Tokenizer:",a.output+".model")
