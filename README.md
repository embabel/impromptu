<img align="left" src="https://github.com/embabel/embabel-agent/blob/main/embabel-agent-api/images/315px-Meister_der_Weltenchronik_001.jpg?raw=true" width="180">

![Build](https://github.com/embabel/rag-demo/actions/workflows/maven.yml/badge.svg)

![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring](https://img.shields.io/badge/spring-%236DB33F.svg?style=for-the-badge&logo=spring&logoColor=white)
![Apache Tomcat](https://img.shields.io/badge/apache%20tomcat-%23F8DC75.svg?style=for-the-badge&logo=apache-tomcat&logoColor=black)
![Apache Maven](https://img.shields.io/badge/Apache%20Maven-C71A36?style=for-the-badge&logo=Apache%20Maven&logoColor=white)
![ChatGPT](https://img.shields.io/badge/chatGPT-74aa9c?style=for-the-badge&logo=openai&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/github%20actions-%232671E5.svg?style=for-the-badge&logo=githubactions&logoColor=white)
![IntelliJ IDEA](https://img.shields.io/badge/IntelliJIDEA-000000.svg?style=for-the-badge&logo=intellij-idea&logoColor=white)

&nbsp;&nbsp;&nbsp;&nbsp;

&nbsp;&nbsp;&nbsp;&nbsp;

# Impromptu - Classical Music Discovery Chatbot

This project demonstrates Retrieval-Augmented Generation (RAG) using Embabel Agent with Apache Lucene for vector storage
and Spring Shell for interaction.

## Getting Started

### Prerequisites

**API Key**: Set at least one LLM provider API key as an environment variable:

```bash
# For OpenAI (GPT models)
export OPENAI_API_KEY=sk-...

# For Anthropic (Claude models)
export ANTHROPIC_API_KEY=sk-ant-...
```

The model configured in `application.yml` determines which key is required. The default configuration uses OpenAI.

**Java**: Java 21+ is required.

### Quick Start

1. Set your API key (see above)
2. Run the shell:
   ```bash
   ./scripts/shell.sh
   ```
3. Ingest a document:
   ```
   ingest
   ```
4. Start chatting:
   ```
   chat
   ```

## Web Interface (Vaadin)

Impromptu includes a browser-based chat interface built with Vaadin, featuring a dark theme inspired by concert halls.

### Running the Web App

```bash
./mvnw spring-boot:run
```

The app runs on **port 8888** (double the 88 piano keys) at http://127.0.0.1:8888/chat

**Important:** Use `127.0.0.1` (loopback address), not `localhost`, for OAuth to work correctly with both Google and Spotify.

### Google OAuth2 Authentication

The web interface supports Google OAuth2 for user authentication. To enable it:

1. Go to https://console.cloud.google.com/
2. Create a new project or select an existing one
3. Navigate to **APIs & Services > Credentials**
4. Create an **OAuth client ID** (Web application type)
5. Add authorized JavaScript origins: `http://127.0.0.1:8888`
6. Add authorized redirect URIs: `http://127.0.0.1:8888/login/oauth2/code/google`
7. Set environment variables with your credentials:

```bash
export GOOGLE_CLIENT_ID="your-client-id.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="your-client-secret"
```

Without these credentials, the app falls back to anonymous user mode.

### Spotify Integration (Optional)

After logging in with Google, users can link their Spotify account to enable playlist management through the chatbot.

To enable Spotify integration:

1. Go to https://developer.spotify.com/dashboard
2. Create an app (or select existing)
3. Add redirect URI: `http://127.0.0.1:8888/callback/spotify` (loopback, not localhost)
4. In **User Management**, add your Spotify email as a user (required for development mode)
5. Set environment variables:

```bash
export SPOTIFY_CLIENT_ID="your-spotify-client-id"
export SPOTIFY_CLIENT_SECRET="your-spotify-client-secret"
```

Once configured, a "Link Spotify" button appears in the header after Google login. The chatbot can then:
- List your Spotify playlists
- Search for tracks
- Create new playlists
- Add tracks to playlists

### Features

- **Dark Concert Hall Theme**: Elegant dark theme with gold accents, inspired by classical concert venues
- **Knowledge Base Panel**: Collapsible panel showing extracted propositions from conversations
- **Real-time Chat**: Streaming responses from the RAG-powered chatbot
- **User Authentication**: Optional Google OAuth2 login
- **Spotify Integration**: Link your Spotify account to create and manage playlists through the chatbot

## Usage

Run the shell script to start Embabel under Spring Shell:

```bash
./scripts/shell.sh
```

You can also run the main class, `com.embabel.examples.ragbot.RagShellApplication`, directly from your IDE.

### Shell Commands

| Command                   | Description                                                                                                                                                                                                                                                   |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ingest [url]`            | Ingest a URL into the RAG store. Uses Apache Tika to parse content hierarchically and chunks it for vector storage. Default URL is the text of the recent Australia Social Media ban for under 16s. Documents are only ingested if they don't already exist.  |
| `ingest-directory <path>` | Ingest all markdown (`.md`) and text (`.txt`) files from a directory recursively. Useful for loading preprocessed content from docling or other sources.                                                                                                      |
| `zap`                     | Clear all documents from the Lucene index. Returns the count of deleted documents.                                                                                                                                                                            |
| `chunks`                  | Display all stored chunks with their IDs and content. Useful for debugging what content has been indexed.                                                                                                                                                     |
| `chat`                    | Start an interactive chat session where you can ask questions about ingested content.                                                                                                                                                                         |
| `uichat [port]`           | Launch a web-based chat UI using [Javelit](https://javelit.io). Opens at http://localhost:8888 by default. Use `uichat-stop` to stop.                                                                                                                         |
| `info`                    | Show Lucene store info: number of chunks, index size, etc.                                                                                                                                                                                                    |

### Web Chat UI

The `uichat` command launches a browser-based chat interface built with [Javelit](https://javelit.io):

![Web Chat UI](images/meyerbeer.jpg)

### Example Workflow

```bash
# Start the shell
./scripts/shell.sh

# Ingest a document
ingest https://example.com/document

# View what was indexed
chunks

# Chat with the RAG-powered assistant
chat
> What does this document say about X?

# Clear the index when done
zap
```

## Troubleshooting

### No Output in Chat

If you're not seeing LLM responses in the chat session, the output may be redirected to a log file. Set `redirect-log-to-file` to `false` in `application.yml`:

```yaml
embabel:
  agent:
    shell:
      redirect-log-to-file: false
```

Alternatively, you can tail the log file in a separate terminal to see output:

```bash
tail -f logs/chat-session.log
```

### Poor Quality RAG Results

First, check the state of the Lucene index by running the `info` command in the shell.
If you do have content, run the `chunks` command to see what has been indexed.

If ingested content is present but you're seeing poor results, it may be that the source was in a format (like complex HTML) that didn't parse cleanly. Consider using [docling](#preprocessing-with-docling) to convert complex documents to clean markdown before ingestion.

You may also want to adjust chunking parameters in `application.yml` to better suit your content:

```yaml
ragbot:
  chunker-config:
    max-chunk-size: 800      # Increase for longer chunks
    overlap-size: 100        # Increase for more context overlap
``` 

## Implementation

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Spring Shell                                   │
│                                                                             │
│   > chat                                                                    │
│   > What penalties apply to social media platforms?                         │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                             AgentProcess                                    │
│                                                                             │
│   Starts when chat begins. Manages conversation state and action dispatch.  │
│   Listens for triggers (UserMessage) and invokes matching @Action methods.  │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │ UserMessage triggers
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     @Action: ChatActions.respond()                          │
│                                                                             │
│   Fired on each user message. Uses Ai interface to build request:           │
│     context.ai()                                                            │
│         .withLlm(...)                                                       │
│         .withReference(toolishRag)  ◄── ToolishRag added as LLM tool        │
│         .withTemplate("ragbot")                                             │
│         .respondWithSystemPrompt(conversation, ...)                         │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Ai Interface                                     │
│                                                                             │
│   • Renders system prompt from Jinja template                               │
│   • Packages ToolishRag as tool definition for LLM                          │
│   • Sends request to LLM provider (OpenAI / Anthropic)                      │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         LLM (GPT / Claude)                                  │
│                                                                             │
│   Receives prompt + tool definitions. Decides to call tools as needed:      │
│                                                                             │
│   "I need to search for penalty information..."                             │
│         │                                                                   │
│         ▼                                                                   │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │  Tool Call: vectorSearch("penalties social media platforms")        │   │
│   └─────────────────────────────────┬───────────────────────────────────┘   │
│                                     │                                       │
└─────────────────────────────────────┼───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ToolishRag → LuceneSearchOperations                      │
│                                                                             │
│   • Converts query to embedding vector                                      │
│   • Searches ./.lucene-index for similar chunks                             │
│   • Returns relevant content to LLM                                         │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
                        LLM generates final response
                        using retrieved context
                                      │
                                      ▼
                           Response sent to user
```

**Flow Summary:**

1. User types `chat` → **AgentProcess** starts and manages the session
2. User sends a message → triggers `@Action(trigger = UserMessage.class)`
3. **ChatActions.respond()** builds request via **Ai** interface, adding **ToolishRag** with `.withReference()`
4. **Ai** packages prompt + tool definitions, sends to LLM
5. **LLM** decides to call a **ToolishRag** tool to search for relevant content
6. The **ToolishRag** tool queries Lucene index, returns matching chunks to LLM
7. **LLM** generates response using retrieved context → sent back to user
8. Loop continues for each new message until user exits

### RAG Configuration

RAG is configured in [`RagConfiguration.java`](src/main/java/com/embabel/impromptu/RagConfiguration.java):

```java

@Bean
LuceneSearchOperations luceneSearchOperations(
        ModelProvider modelProvider,
        RagbotProperties properties) {
    var embeddingService = modelProvider.getEmbeddingService(DefaultModelSelectionCriteria.INSTANCE);
    var luceneSearchOperations = LuceneSearchOperations
            .withName("docs")
            .withEmbeddingService(embeddingService)
            .withChunkerConfig(properties.chunkerConfig())
            .withIndexPath(Paths.get("./.lucene-index"))
            .buildAndLoadChunks();
    return luceneSearchOperations;
}
```

Key aspects:

- **Lucene with disk persistence**: The vector index is stored at `./.lucene-index`, surviving application restarts
- **Embedding service**: Uses the configured `ModelProvider` to get an embedding service for vectorizing content
- **Configurable chunking**: Content is split into chunks with configurable size (default 800 chars), overlap (default
  50 chars), and optional section title inclusion

Chunking properties can be configured via `application.yml`:

```yaml
ragbot:
  chunker-config:
    max-chunk-size: 800
    overlap-size: 100
```

### Chatbot Creation

The chatbot is created in [
`ChatConfiguration.java`](src/main/java/com/embabel/impromptu/ChatConfiguration.java):

```java

@Bean
Chatbot chatbot(AgentPlatform agentPlatform) {
    return AgentProcessChatbot.utilityFromPlatform(agentPlatform);
}
```

The `AgentProcessChatbot.utilityFromPlatform()` method creates a chatbot that automatically discovers all `@Action`
methods in `@EmbabelComponent` classes. Any action with a matching trigger becomes eligible to be called when
appropriate messages arrive.

### Action Handling

Chat actions are defined in [`ChatActions.java`](src/main/java/com/embabel/impromptu/ChatActions.java):

```java

@EmbabelComponent
public class ChatActions {

    private final ToolishRag toolishRag;
    private final RagbotProperties properties;

    public ChatActions(SearchOperations searchOperations, RagbotProperties properties) {
        this.toolishRag = new ToolishRag(
                "sources",
                "Sources for answering user questions",
                searchOperations);
        this.properties = properties;
    }

    @Action(canRerun = true, trigger = UserMessage.class)
    void respond(Conversation conversation, ActionContext context) {
        var assistantMessage = context.ai()
                .withLlm(properties.chatLlm())
                .withReference(toolishRag)
                .withTemplate("ragbot")
                .respondWithSystemPrompt(conversation, Map.of(
                        "properties", properties
                ));
        context.sendMessage(conversation.addMessage(assistantMessage));
    }
}
```

Key concepts:

1. **`@EmbabelComponent`**: Marks the class as containing agent actions that can be discovered by the platform

2. **`@Action` annotation**:
    - `trigger = UserMessage.class`: This action is invoked whenever a `UserMessage` is received in the conversation
    - `canRerun = true`: The action can be executed multiple times (for each user message)

3. **`ToolishRag` as LLM reference**:
    - Wraps the `SearchOperations` (Lucene index) as a tool the LLM can use
    - When `.withReference(toolishRag)` is called, the LLM can search the RAG store to find relevant content
    - The LLM decides when to use this tool based on the user's question

4. **Response flow**:
    - User sends a message (triggering the action)
    - The action builds an AI request with the RAG reference
    - The LLM may call the RAG tool to retrieve relevant chunks
    - The LLM generates a response using retrieved context
    - The response is added to the conversation and sent back

### Prompt Templates

Chatbot prompts are managed using Jinja templates rather than inline strings. This is best practice for chatbots
because:

- **Prompts grow complex**: Chatbots require detailed system prompts covering persona, guardrails, objectives, and
  behavior guidelines
- **Separation of concerns**: Prompt engineering can evolve independently from Java code
- **Reusability**: Common elements (guardrails, personas) can be shared across different chatbot configurations
- **Configuration-driven**: Switch personas or objectives via `application.yml` without code changes

#### Separating Voice from Objective

The template system separates two concerns:

- **Objective**: *What* the chatbot should accomplish - the task-specific instructions and domain expertise (e.g.,
  analyzing legal documents, answering technical questions)
- **Voice**: *How* the chatbot should communicate - the persona, tone, and style of responses (e.g., formal lawyer,
  Shakespearean, sarcastic)

This separation allows mixing and matching. You could have a "legal" objective answered in the voice of Shakespeare,
Monty Python, or a serious lawyer - without duplicating the legal analysis instructions in each persona template.

#### Template Structure

```
src/main/resources/prompts/
├── ragbot.jinja                    # Main template entry point
├── elements/
│   ├── guardrails.jinja            # Safety and content restrictions
│   └── personalization.jinja       # Dynamic persona/objective loader
├── personas/                       # HOW to communicate (voice/style)
│   ├── clause.jinja                # Serious legal expert
│   ├── shakespeare.jinja           # Elizabethan style
│   ├── monty_python.jinja          # Absurdist humor
│   └── ...
└── objectives/                     # WHAT to accomplish (task/domain)
    └── legal.jinja                 # Legal document analysis
```

#### How Templates Are Loaded

The main template `ragbot.jinja` composes the system prompt from reusable elements:

```jinja
{% include "elements/guardrails.jinja" %}

{% include "elements/personalization.jinja" %}
```

The `personalization.jinja` template dynamically includes persona and objective based on configuration:

```jinja
{% set persona_template = "personas/" ~ properties.voice().persona() ~ ".jinja" %}
{% include persona_template %}

{% set objective_template = "objectives/" ~ properties.objective() ~ ".jinja" %}
{% include objective_template %}
```

#### Invoking Templates from Code

Templates are invoked using `.withTemplate()` and passing bindings:

```java
context.ai()
    .withLlm(properties.chatLlm())
    .withReference(toolishRag)
    .withTemplate("ragbot")
    .respondWithSystemPrompt(
            conversation, 
         Map.of(
            "properties", properties
    ));
```

The `properties` object (a Java record) is accessible in templates. Jinjava supports calling record accessor methods
with `properties.voice().persona()` syntax for nested records.

To create a new persona, add a `.jinja` file to `prompts/personas/` and reference it by name in `application.yml`.
See [Configuration Reference](#configuration-reference) for all available settings.

### Creating a Custom Objective and Persona

This section walks through creating a new chatbot configuration from scratch, using a film critic example.

#### Step 1: Create the Objective Template

The objective defines *what* the chatbot should accomplish. Create a new file at:

```
src/main/resources/prompts/objectives/discuss_films.jinja
```

Example content based on existing objectives:

```jinja
Answer questions about classic cinema and film history in a clear and engaging manner.

The tools available to you access a curated collection of film reviews and criticism.
You must always use these tools to find answers, as your general knowledge will not extend to everything in the collection
and these tools allow you to find detailed analysis if you try hard enough.

Always back up your points with direct quotes from the film criticism sources.

You may find that the result from one tool call leads to a search for another tool,
e.g. a result mentioning "as discussed in the analysis of Citizen Kane..." might lead to a search for "Citizen Kane analysis".

DO NOT RELY ON GENERAL KNOWLEDGE unless you are certain a better answer is not in the provided sources.
```

#### Step 2: Create the Persona Template

The persona defines *how* the chatbot communicates. Create a new file at:

```
src/main/resources/prompts/personas/film_critic.jinja
```

Example content based on existing personas:

```jinja
Your name is Cinephile.
You are a passionate film critic with deep knowledge of cinema history.
You want to share your love of films with others and help them appreciate the art of filmmaking.
You speak with enthusiasm about cinematography, direction, and storytelling.
```

#### Step 3: Update the Directory Structure

After creating the files, your prompts directory should look like:

```
src/main/resources/prompts/
├── ragbot.jinja
├── elements/
│   ├── guardrails.jinja
│   └── personalization.jinja
├── personas/
│   ├── clause.jinja
│   ├── music-guide.jinja
│   ├── film_critic.jinja          # NEW
│   └── ...
└── objectives/
    ├── legal.jinja
    ├── music.jinja
    ├── discuss_films.jinja         # NEW
    └── ...
```

#### Step 4: Update ChatActions with ToolishRag Description

The `ToolishRag` description in `ChatActions.java` helps the LLM understand what content is available. Update the constructor to describe your new content:

```java
public ChatActions(
        SearchOperations searchOperations,
        RagbotProperties properties) {
    this.toolishRag = new ToolishRag(
            "sources",
            "Film reviews and criticism: Classic cinema analysis and reviews",  // Updated description
            searchOperations)
            .withHint(TryHyDE.usingConversationContext());
    this.properties = properties;
}
```

The description should briefly explain what content the RAG store contains, helping the LLM make better decisions about when and how to search.

#### Step 5: Ingest Your Content

Use the `ingest-directory` command to load a directory of markdown or text files:

```bash
# Start the shell
./scripts/shell.sh

# Ingest a directory of film reviews (markdown or text files)
ingest-directory /path/to/film-reviews

# Verify content was indexed
chunks
```

The `ingest-directory` command recursively processes all `.md` and `.txt` files in the specified directory, chunking them for vector storage.

#### Step 6: Configure application.yml

Finally, update your configuration to use the new objective and persona:

```yaml
ragbot:
  voice:
    persona: film_critic           # References personas/film_critic.jinja
    max-words: 50

  objective: discuss_films         # References objectives/discuss_films.jinja

  chat-llm:
    model: gpt-4.1-mini
    temperature: 0.3               # Slightly creative for engaging film discussion
```

#### Complete Example Summary

| File | Purpose |
|------|---------|
| `prompts/objectives/discuss_films.jinja` | Defines the task: answering questions about films |
| `prompts/personas/film_critic.jinja` | Defines the voice: enthusiastic cinema expert |
| `ChatActions.java` (constructor) | Describes the RAG content for the LLM |
| `application.yml` | Wires everything together |

Restart the application after making these changes:

## Configuration Reference

All configuration is externalized in `application.yml`, allowing behavior changes without code modifications.

### application.yml Reference

```yaml
ragbot:
  # RAG chunking settings
  chunker-config:
    max-chunk-size: 800      # Maximum characters per chunk
    overlap-size: 100        # Overlap between chunks for context continuity

  # LLM model selection and hyperparameters
  chat-llm:
    model: gpt-4.1-mini      # Model to use for chat responses
    temperature: 0.0         # 0.0 = deterministic, higher = more creative

  # Voice controls HOW the chatbot communicates
  voice:
    persona: clause          # Which persona template to use (personas/*.jinja)
    max-words: 30            # Hint for response length

  # Objective controls WHAT the chatbot accomplishes
  objective: legal           # Which objective template to use (objectives/*.jinja)

embabel:
  agent:
    shell:
      # Redirect logging during chat sessions
      redirect-log-to-file: true
```

### Logging During Chat Sessions

When `redirect-log-to-file: true`, console logging is redirected to a file during chat sessions, providing a cleaner
chat experience. Logs are written to:

```
logs/chat-session.log
```

To monitor logs while chatting, open a separate terminal and tail the log file:

```bash
tail -f logs/chat-session.log
```

This is useful for debugging RAG retrieval, seeing which chunks are being returned, and monitoring LLM API calls.

### Switching Personas and Models

To change the chatbot's personality, simply update the `persona` value:

```yaml
ragbot:
  voice:
    persona: shakespeare     # Now responds in Elizabethan English
```

To use a different LLM:

```yaml
ragbot:
  chat-llm:
    model: gpt-4.1           # Use the larger GPT-4.1 instead
    temperature: 0.7         # More creative responses
```

No code changes required - just restart the application.

## Preprocessing with docling

[Docling](https://ds4sd.github.io/docling/) is a document conversion tool that excels at converting complex formats (PDF, Word, HTML, PowerPoint) to clean markdown. This is useful when source documents don't parse well with standard tools.

**Note:** Docling can be slow, especially for large or complex documents. Plan accordingly.

### Installation

You'll need Python. It's good practice to set up a virtual environment first:

```bash
# Using venv (Python standard library)
python -m venv docling-env
source docling-env/bin/activate  # On Windows: docling-env\Scripts\activate

# Or using conda
conda create -n docling python=3.11
conda activate docling
```

Then install docling:

```bash
pip install docling
```

### Usage

Convert a single file to markdown:

```bash
docling document.pdf --to md --output output_dir/
```

Convert all files in a directory:

```bash
docling input_dir/ --to md --output output_dir/
```

### When to Use docling

- PDF documents with complex layouts, tables, or images
- HTML pages that don't parse cleanly with Tika
- Word documents with formatting that needs preservation
- Any document where the default ingestion produces poor chunking

After converting to markdown, use `ingest-directory` to load the cleaned content:

```bash
ingest-directory output_dir/
```

See the [docling documentation](https://ds4sd.github.io/docling/) for more options and advanced usage.
