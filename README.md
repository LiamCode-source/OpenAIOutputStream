# OpenAIOutputStream - Version 2.0
Mendix module used for streaming OpenAI outputs.

Using the REST API action for OpenAI in Mendix is the perfect solution... until you need to stream the output.

This module provides Java and JS actions which can be used to stream an OpenAI output as the chunks are recieved, allowing the output to be streamed to the end user as the output is generated.

The provided JS actions also provide additional optional functionalities, such as a loading bar and scrolling to the end of a message as it is generated.


## JS Actions

- **JS_OpenAI_ProxyStream**: Sets up an OpenAI proxy which can be used for streaming responses to user. 
This stops api keys being exposed via nanoflows.

- **JS_OpenAI_ProxyStreamFromJson**: Uses the OpenAI proxy to allow customised JSON to be sent to the AI. To stream output, ensure the stream parameter is added to the JSON.

## Java Actions

- **ASU_OpenAI_StreamProxy**: Use this in your after startup microflow. This sets up an OpenAI proxy which can be used for streaming responses to user. 
This stops api keys being exposed via nanoflows.

</br>
</br>

## Legacy JS Actions

**IMPORTANT**
*Using these JS actions expose the API key to the client side. Therefore if end users should not have access to the OpenAI key(s), then the newer actions which use the OpenAI Stream Proxy should be used instead*

- **JS_OpenAI_OutputStream**: Takes a text input and streams out the output
- **JS_OpenAI_OutputFromJson**: Allows customised JSON to be sent to the AI. To stream output, ensure the stream parameter is added to the JSON.
