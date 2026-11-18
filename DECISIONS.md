# Decisions

Answer these before the review. A short paragraph each is enough. We will walk through them live.

## 1. State architecture and render isolation

How did you structure state so a tick for one item does not re-render unrelated rows?

## 2. High-frequency ingestion strategy

How would your implementation behave if the tick rate jumped from 2/sec to 100/sec across 1,000 items?

## 3. Trade-offs made for the 2-hour cap

What did you leave rough, and what would you do with another day?

## 4. AI usage note

Where did an assistant accelerate you, and where did you have to override or fix its output?
