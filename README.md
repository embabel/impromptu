<img align="left" src="https://github.com/embabel/embabel-agent/blob/main/embabel-agent-api/images/315px-Meister_der_Weltenchronik_001.jpg?raw=true" width="180">

![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring](https://img.shields.io/badge/spring-%236DB33F.svg?style=for-the-badge&logo=spring&logoColor=white)
![Apache Maven](https://img.shields.io/badge/Apache%20Maven-C71A36?style=for-the-badge&logo=Apache%20Maven&logoColor=white)
![ChatGPT](https://img.shields.io/badge/chatGPT-74aa9c?style=for-the-badge&logo=openai&logoColor=white)
![Neo4j](https://img.shields.io/badge/Neo4j-008CC1?style=for-the-badge&logo=neo4j&logoColor=white)
![IntelliJ IDEA](https://img.shields.io/badge/IntelliJIDEA-000000.svg?style=for-the-badge&logo=intellij-idea&logoColor=white)

&nbsp;&nbsp;&nbsp;&nbsp;

&nbsp;&nbsp;&nbsp;&nbsp;

# Impromptu - Classical Music Discovery Chatbot

Chatbot intended to help users discover classical music.

Embabel features:

- Agent-based chatbot with RAG (Neo4j vector storage)
- Proposition extraction pipeline for memories about users
- Spotify integration for playlist management

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

**Docker**: Required for running Neo4j.

### Starting Neo4j

The application uses Neo4j as its vector store for RAG. Start it with Docker Compose:

```bash
docker compose up -d
```

This starts Neo4j with:
- **Bolt port**: `7888` (for application connections)
- **HTTP port**: `8889` (for Neo4j Browser at http://localhost:8889)
- **Credentials**: `neo4j` / `brahmsian`

To stop Neo4j:
```bash
docker compose down
```

To wipe all data and start fresh:
```bash
docker compose down -v
```

### Loading Open Opus Data

The application can load composer and works data from [Open Opus](https://openopus.org/), a free, open-source classical music database.

**Load into Neo4j** (with the app running):
```bash
# Load data (fetches directly from Open Opus API)
curl -X POST http://localhost:8888/api/openopus/load

# Clear all Open Opus data
curl -X DELETE http://localhost:8888/api/openopus
```

This creates a normalized graph with:
- **Composer** nodes linked to **Epoch** (Baroque, Romantic, etc.)
- **Work** nodes linked to **Genre** (Orchestral, Chamber, Keyboard, etc.)
- **COMPOSED** relationships connecting composers to their works

Example Cypher queries after loading:
```cypher
// Find all Romantic composers
MATCH (c:Composer)-[:OF_EPOCH]->(e:Epoch {name: "Romantic"})
RETURN c.completeName

// Find all orchestral works by Brahms
MATCH (c:Composer {name: "Brahms"})-[:COMPOSED]->(w:Work)-[:OF_GENRE]->(g:Genre {name: "Orchestral"})
RETURN w.title

// Count works by genre
MATCH (w:Work)-[:OF_GENRE]->(g:Genre)
RETURN g.name, count(w) as works ORDER BY works DESC
```

### Running the Web App

After Neo4j is running:

```bash
./mvnw spring-boot:run
```

The app runs on **port 8888** (double the 88 piano keys) at http://127.0.0.1:8888/chat

A "Neo4j Browser" link in the footer opens the database UI with credentials pre-filled.

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
- **Neo4j Browser**: Direct link to explore the graph database

## Implementation Details

### Neo4j Vector Storage

The application uses Neo4j as its vector store for RAG, configured via `application.yml`:

```yaml
database:
  datasources:
    neo:
      type: NEO4J
      host: ${NEO4J_HOST:localhost}
      port: ${NEO4J_PORT:7888}
      user-name: ${NEO4J_USERNAME:neo4j}
      password: ${NEO4J_PASSWORD:brahmsian}
      database-name: ${NEO4J_DATABASE:neo4j}

neo4j:
  http:
    port: ${NEO4J_HTTP_PORT:8889}
```

Key aspects:

- **Neo4j with vector indexes**: Chunks are stored as nodes with vector embeddings for similarity search
- **Graph relationships**: Content relationships can be modeled as edges in the graph
- **Persistent storage**: Data survives container restarts (stored in Docker volume)
- **Configurable chunking**: Content is split into chunks with configurable size (default 800 chars) and overlap (default 100 chars)
- **Admin queries**: See [`cypher/admin.cypher`](cypher/admin.cypher) for useful queries to inspect and manage the database

Chunking properties can be configured via `application.yml`:

```yaml
impromptu:
  neo-rag:
    max-chunk-size: 800
    overlap-size: 100
```

### Chatbot Creation

The chatbot is created in [`ChatConfiguration.java`](src/main/java/com/embabel/impromptu/chat/ChatConfiguration.java):

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

Chat actions are defined in [`ChatActions.java`](src/main/java/com/embabel/impromptu/chat/ChatActions.java):

```java
@EmbabelComponent
public class ChatActions {

    private final ToolishRag toolishRag;
    private final ImpromptuProperties properties;
    private final SpotifyService spotifyService;

    public ChatActions(
            SearchOperations searchOperations,
            SpotifyService spotifyService,
            ApplicationEventPublisher eventPublisher,
            ImpromptuProperties properties) {
        this.toolishRag = new ToolishRag(
                "sources",
                "The music criticism written by Robert Schumann: His own writings",
                searchOperations)
                .withHint(TryHyDE.usingConversationContext());
        this.spotifyService = spotifyService;
        this.properties = properties;
    }

    @Action(canRerun = true, trigger = UserMessage.class)
    void respond(Conversation conversation, ImpromptuUser user, ActionContext context) {
        List<Object> tools = new LinkedList<>();
        if (user.isSpotifyLinked()) {
            tools.add(new SpotifyTools(user, spotifyService));
        }
        var assistantMessage = context.ai()
                .withLlm(properties.chatLlm())
                .withPromptContributor(user)
                .withReference(toolishRag)
                .withToolObjects(tools)
                .withTemplate("ragbot")
                .respondWithSystemPrompt(conversation, Map.of(
                        "properties", properties,
                        "voice", properties.voice(),
                        "objective", properties.objective()
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
   - Wraps the `SearchOperations` (Neo4j vector store) as a tool the LLM can use
   - When `.withReference(toolishRag)` is called, the LLM can search the RAG store to find relevant content
   - The LLM decides when to use this tool based on the user's question

4. **Spotify tools**: When the user has linked their Spotify account, `SpotifyTools` is added as a tool object, enabling playlist management

### Prompt Templates

Chatbot prompts are managed using Jinja templates rather than inline strings. This is best practice for chatbots
because:

- **Prompts grow complex**: Chatbots require detailed system prompts covering persona, guardrails, objectives, and behavior guidelines
- **Separation of concerns**: Prompt engineering can evolve independently from Java code
- **Reusability**: Common elements (guardrails, personas) can be shared across different chatbot configurations
- **Configuration-driven**: Switch personas or objectives via `application.yml` without code changes

#### Separating Voice from Objective

The template system separates two concerns:

- **Objective**: *What* the chatbot should accomplish - the task-specific instructions and domain expertise
- **Voice**: *How* the chatbot should communicate - the persona, tone, and style of responses

This separation allows mixing and matching. You could have a "music" objective answered in the voice of Shakespeare or a different persona without duplicating instructions.

#### Template Structure

```
src/main/resources/prompts/
├── ragbot.jinja                    # Main template entry point
├── elements/
│   ├── guardrails.jinja            # Safety and content restrictions
│   └── personalization.jinja       # Dynamic persona/objective loader
├── personas/                       # HOW to communicate (voice/style)
│   ├── impromptu.jinja             # Default: friendly music guide
│   ├── shakespeare.jinja           # Elizabethan style
│   ├── bible.jinja                 # Biblical style
│   ├── adaptive.jinja              # Adapts to user
│   └── jesse.jinja                 # Casual style
└── objectives/                     # WHAT to accomplish (task/domain)
    ├── music.jinja                 # Classical music education (default)
    └── legal.jinja                 # Legal document analysis
```

#### How Templates Are Loaded

The main template `ragbot.jinja` composes the system prompt from reusable elements:

```jinja
{% include "elements/guardrails.jinja" %}

{% include "elements/personalization.jinja" %}

Keep your responses under {{ properties.voice().maxWords() }} words unless they
MUST be longer for a detailed response or quoting content.
```

The `personalization.jinja` template dynamically includes persona and objective based on configuration:

```jinja
{% set persona_template = "personas/" ~ voice.persona() ~ ".jinja" %}
{% include persona_template %}

{% set objective_template = "objectives/" ~ objective ~ ".jinja" %}
{% include objective_template %}
```

## Configuration Reference

All configuration is externalized in `application.yml`, allowing behavior changes without code modifications.

### application.yml Reference

```yaml
database:
  datasources:
    neo:
      host: localhost
      port: 7888               # Neo4j Bolt port
      user-name: neo4j
      password: brahmsian

neo4j:
  http:
    port: 8889                 # Neo4j Browser HTTP port

impromptu:
  # RAG chunking settings
  neo-rag:
    max-chunk-size: 800        # Maximum characters per chunk
    overlap-size: 100          # Overlap between chunks for context continuity

  # LLM model selection and hyperparameters
  chat-llm:
    model: gpt-4.1-mini        # Model to use for chat responses
    temperature: 0.0           # 0.0 = deterministic, higher = more creative

  # Voice controls HOW the chatbot communicates
  voice:
    persona: impromptu         # Which persona template to use (personas/*.jinja)
    max-words: 250             # Hint for response length

  # Objective controls WHAT the chatbot accomplishes
  objective: music             # Which objective template to use (objectives/*.jinja)

embabel:
  models:
    default-llm:
      model: gpt-4.1-mini
    default-embedding-model:
      model: text-embedding-3-small
```

### Switching Personas

To change the chatbot's personality, simply update the `persona` value:

```yaml
impromptu:
  voice:
    persona: shakespeare     # Now responds in Elizabethan English
```

To use a different LLM:

```yaml
impromptu:
  chat-llm:
    model: gpt-4.1           # Use the larger GPT-4.1 instead
    temperature: 0.7         # More creative responses
```

No code changes required - just restart the application.

## Miscellaneous

### Killing a Stuck Server Process

If your IDE dies or the server doesn't shut down cleanly, you may need to manually kill the process on port 8888:

```bash
lsof -ti :8888 | xargs kill -9
```
