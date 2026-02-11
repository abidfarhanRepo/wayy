#!/usr/bin/env python3
import json
import time
import argparse
from pathlib import Path

def load_agents(path: Path):
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)

def run_agent(agent, simulate=True, delay=1.0):
    print(f"\n=== Scheduling Agent {agent['id']} - {agent['name']} ===")
    print(f"Role: {agent['role']}\n")
    print("Prompt:\n")
    print(agent['prompt'])
    if simulate:
        print('\n[SIMULATION] Waiting for simulated LLM output...')
        time.sleep(delay)
        print(f"[SIMULATION] Agent {agent['id']} completed with simulated output.\n")
    else:
        print('\n[INFO] Replace simulation with real LLM call in this script.')

def main():
    parser = argparse.ArgumentParser(description='Simple multi-agent orchestrator (simulation)')
    parser.add_argument('--agents-file', type=str, default='agents_def.json')
    parser.add_argument('--simulate', action='store_true', default=True, help='Simulate LLM responses (default)')
    parser.add_argument('--delay', type=float, default=0.5, help='Delay per agent simulation')
    args = parser.parse_args()

    base = Path(__file__).resolve().parent
    agents_path = base / args.agents_file
    cfg = load_agents(agents_path)
    print(f"Loaded {len(cfg.get('agents', []))} agents (max_concurrent={cfg.get('max_concurrent_requests')})")

    for agent in cfg.get('agents', []):
        run_agent(agent, simulate=args.simulate, delay=args.delay)

    print('\nAll agents scheduled.')

if __name__ == '__main__':
    main()
