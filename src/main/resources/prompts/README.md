# Prompt templates go here

Prompts used by `PromptRunner.withTemplate` or via the `TemplateRenderer` interface
should go in this directory, with a `.jinja` extension.

For example, if you reference `my_prompt` in your code,
you should create a file `my_prompt.jinja` in this directory.

## Creating Personas

Personas define the assistant's voice and personality. They live in the `personas/` subdirectory.

### To create a new persona:

1. Create a file `personas/yourname.jinja`
2. Add a description comment on the **first line**:
   ```
   {# description: Short description of the persona #}
   ```
3. Write the system prompt that defines the persona's voice

### Example persona file (`personas/friendly.jinja`):

```jinja
{# description: Warm and approachable conversational style #}
You are warm and friendly in your responses.
You use casual language and make the user feel comfortable.
```

### Available personas:

- `impromptu` - Friendly and enthusiastic classical music guide (default)
- `shakespeare` - The Bard himself, speaking in poetic Elizabethan style
- `jesse` - Jesse Pinkman from Breaking Bad - casual and colorful. NSFW
- `bible` - Biblical Old Testament verse style
- `adaptive` - Adapts to match your communication style