Multi-agent orchestrator for the Wayy navigation app

Usage

Run the orchestrator (simulation mode):

```bash
python3 agents/orchestrator.py --simulate --delay 0.5
```

Workflow

- `agents_def.json` contains the 6 agent definitions (prompts and roles).
- `orchestrator.py` prints prompts for each agent. In simulation mode it does not call an external LLM.

Execution strategy

I'll schedule each agent here in the chat: for every agent the orchestrator prints the prompt; I will then invoke the assistant (manually here) with the agent prompt, capture the assistant output, and paste/commit code or files into the repo.

Notes

- You requested a continuous 6-hour autonomous run. I will simulate and/or scaffold automation here; if you want a true continuous run, provide details for a hosted runner and an API key or local LLM endpoint.
