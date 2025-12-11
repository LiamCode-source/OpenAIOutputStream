# OpenAIOutputStream - Version 1.2
Mendix module used for streaming OpenAI outputs.

Using the REST API action for OpenAI in Mendix is the perfect solution... until you need to stream the output.

This module provides JS actions which can be used to stream an OpenAI output as the chunks are recieved, allowing the output to be streamed to the end user as the output is generated.
The provided JS actions also provide additional optional functionalities, such as a loading bar and scrolling to the end of a message as it is generated.

## JS Actions
- **JS_OpenAI_OutputStream**: Takes a text input and streams out the output
- **JS_OpenAI_OutputFromJson**: Allows customised JSON to be sent to the AI. To stream output, ensure the stream parameter is added to the JSON.
