import os
from pathlib import Path

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer

BASE_MODEL = os.getenv("BASE_MODEL", "Qwen/Qwen2.5-0.5B-Instruct")
adapter_dir = Path(os.getenv("ADAPTER_DIR", "output/aibondhu-lora"))
merged_dir = Path(os.getenv("MERGED_DIR", "output/aibondhu-merged"))
merged_dir.mkdir(parents=True, exist_ok=True)

print(f"Loading base model: {BASE_MODEL}")
# Load the base model explicitly, then attach the adapter. AutoPeftModel tries
# to auto-load tokenizer metadata from the adapter folder, which can contain
# an incompatible extra_special_tokens field from newer tokenizer versions.
model = AutoModelForCausalLM.from_pretrained(
    BASE_MODEL,
    torch_dtype=torch.float16,
    low_cpu_mem_usage=True,
    device_map="cpu",
)
print(f"Loading LoRA adapter: {adapter_dir}")
model = PeftModel.from_pretrained(model, str(adapter_dir))
model = model.merge_and_unload()
model.save_pretrained(str(merged_dir), safe_serialization=True, max_shard_size="2GB")

tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL, use_fast=True)
tokenizer.save_pretrained(str(merged_dir))
print(f"Merged model saved to {merged_dir}")
