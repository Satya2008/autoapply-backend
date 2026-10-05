# ADR 0001: Where job and resume vectors live

- Status: accepted
- Date: 2026-10-04
- Phase: 18 (Advanced AI)

## Context

Semantic matching and RAG need text embeddings: one vector per job, per profile and per
resume chunk, and a search for the nearest vectors. The data lives in MySQL, one database per
service. The plan named two options:

1. Move to PostgreSQL with pgvector.
2. Keep MySQL and add a separate vector database (Qdrant).

The scale today: a few thousand active jobs in the 60-day matching window, a resume of 10-60
chunks per user. The development machine has 4 GB of RAM and no Docker.

## Decision

Neither, for now: **keep MySQL, store vectors as float32 blobs, search in memory.**

- `job_embeddings` (matching-service): one row per job and embedding model, with the hash of
  the embedded text, so a job is embedded once per model and again only when its text changes.
- `embedding_cache` (matching-service): vectors from paid models keyed by model and text hash,
  so the same text (a resume chunk, a question) is never paid for twice.
- `JobVectorIndex`: each matching-service instance holds the current model's job vectors in
  memory and searches them by brute force (cosine on unit vectors is a dot product). It
  catches up from the table incrementally, so vectors written by another instance show up.
- Resume chunks are few per user: they are ranked on the fly by `/internal/v1/ai/retrieve`,
  no index needed.

Vectors from different models never meet: every vector carries its model key, a model switch
starts a new index, and a failing provider makes the whole call fall back to the local embedder.

## Why

- **Size.** 20,000 jobs x 512 dimensions x 4 bytes is about 40 MB; a brute-force search over
  5,000 jobs takes a few milliseconds. An approximate index (HNSW) pays off from hundreds of
  thousands of vectors, which we are far from.
- **One less moving part.** No migration of four services' data to Postgres, no extra server to
  run, back up and secure; the laptop can still run everything.
- **Exact results.** Brute force has perfect recall; ANN indexes trade a little recall for speed.

## When to revisit

Move to a real vector store (pgvector if the services move to Postgres anyway, else Qdrant)
when any of these holds:

- more than ~200,000 vectors per model, or the in-memory index passes ~10% of the heap;
- vector search needs filters the database should do (by location, salary) before ranking;
- several services need to search the same vectors.

The search sits behind `JobVectorIndex` and `EmbeddingStore`, so the swap stays inside
matching-service.

## Consequences

- Each instance spends memory on its own copy of the index and catches up every 30 seconds
  before a search; a vector written elsewhere can take that long to become searchable (the
  match run that wrote it sees it at once).
- Startup reads the window's vectors once; with the cap (`max-indexed-jobs`) memory stays bounded.
