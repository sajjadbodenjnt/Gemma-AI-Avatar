import os, json
from datasets import Dataset
from transformers import AutoTokenizer, AutoModelForCausalLM, TrainingArguments, Trainer, DataCollatorForLanguageModeling
from peft import LoraConfig, get_peft_model, TaskType

BASE = os.getenv("BASE_MODEL", "Qwen/Qwen2.5-0.5B-Instruct")
DATA = os.getenv("TRAIN_FILE", "training/data/bengali_instructions.jsonl")
OUT = os.getenv("OUTPUT_DIR", "output/aibondhu-lora")

rows=[]
with open(DATA, encoding="utf-8") as f:
    for line in f:
        if line.strip():
            item=json.loads(line)
            prompt=f"<|im_start|>user\n{item['instruction']}\n{item.get('input','')}<|im_end|>\n<|im_start|>assistant\n{item['output']}<|im_end|>"
            rows.append({"text":prompt})
if len(rows)<2:
    raise RuntimeError("Training dataset is too small; add at least two examples.")

tokenizer=AutoTokenizer.from_pretrained(BASE, use_fast=True)
if tokenizer.pad_token is None:
    tokenizer.pad_token=tokenizer.eos_token
model=AutoModelForCausalLM.from_pretrained(BASE, low_cpu_mem_usage=True)
model.config.use_cache=False
lora=LoraConfig(task_type=TaskType.CAUSAL_LM, r=8, lora_alpha=16, lora_dropout=0.05,
                target_modules=["q_proj","k_proj","v_proj","o_proj"])
model=get_peft_model(model,lora)
def tokenize(batch):
    return tokenizer(batch["text"], truncation=True, max_length=384, padding="max_length")
dataset=Dataset.from_list(rows).map(tokenize, batched=True, remove_columns=["text"])
args=TrainingArguments(
    output_dir=OUT, num_train_epochs=float(os.getenv("EPOCHS","1")),
    per_device_train_batch_size=1, gradient_accumulation_steps=4,
    learning_rate=2e-4, logging_steps=1, save_strategy="epoch",
    report_to="none", fp16=False, bf16=False, use_cpu=True,
    dataloader_pin_memory=False
)
trainer=Trainer(model=model,args=args,train_dataset=dataset,
                data_collator=DataCollatorForLanguageModeling(tokenizer=tokenizer,mlm=False))
trainer.train()
model.save_pretrained(OUT)
tokenizer.save_pretrained(OUT)
print(f"Saved LoRA adapter to {OUT}")
