import os
from pathlib import Path
import torch
from peft import AutoPeftModelForCausalLM
from transformers import AutoTokenizer

adapter_dir = Path(os.getenv("ADAPTER_DIR", "output/aibondhu-lora"))
merged_dir = Path(os.getenv("MERGED_DIR", "output/aibondhu-merged"))
merged_dir.mkdir(parents=True, exist_ok=True)

print("Loading trained LoRA adapter and its base model...")
model = AutoPeftModelForCausalLM.from_pretrained(
    str(adapter_dir),
    torch_dtype=torch.float16,
    low_cpu_mem_usage=True,
    device_map="cpu",
)
model = model.merge_and_unload()
model.save_pretrained(str(merged_dir), safe_serialization=True, max_shard_size="2GB")
tokenizer = AutoTokenizer.from_pretrained(str(adapter_dir), use_fast=True)
tokenizer.save_pretrained(str(merged_dir))
print(f"Merged model saved to {merged_dir}")
