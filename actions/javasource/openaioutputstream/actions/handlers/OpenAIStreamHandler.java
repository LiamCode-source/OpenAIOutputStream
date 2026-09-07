package openaioutputstream.actions.handlers;

import com.mendix.externalinterface.connector.RequestHandler;
import com.mendix.m2ee.api.IMxRuntimeRequest;
import com.mendix.m2ee.api.IMxRuntimeResponse;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.mendix.core.Core;
import com.mendix.systemwideinterfaces.core.ISession;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class OpenAIStreamHandler extends RequestHandler {
	
	private final String apiKey;
    private final String endpoint;
	private openaioutputstream.proxies.ENUM_AI_Type apiType;
	private final int maxRequestSize;

    public OpenAIStreamHandler(String apiKey, String endpoint, openaioutputstream.proxies.ENUM_AI_Type apiType, int maxRequestSize) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
		this.apiType = apiType;
		this.maxRequestSize = maxRequestSize;
    }

    @Override
    public void processRequest(
            IMxRuntimeRequest request,
            IMxRuntimeResponse response,
            String path
    ) throws Exception {

        HttpServletRequest servletRequest = request.getHttpServletRequest();
        HttpServletResponse servletResponse = response.getHttpServletResponse();
		
		ISession session = getSessionFromRequest(request); // Checks for authentic user session

		// OPTIONS requests without auth
		if ("OPTIONS".equals(servletRequest.getMethod())) {
			servletResponse.setStatus(HttpServletResponse.SC_NO_CONTENT);
			servletResponse.flushBuffer();
            return;
		}
		
		if (session == null) {
            Core.getLogger("OpenAIStream").warn("Unauthorised proxy streaming attempt.");
			servletResponse.setStatus(401);
            servletResponse.setContentType("application/json");
			try (OutputStream out = servletResponse.getOutputStream()) {
                String json = "{\"error\": \"Unauthorised proxy streaming attempt.\"}";
                out.write(json.getBytes("UTF-8"));
                out.flush();
             }
            servletResponse.flushBuffer();
            return;
        }
		
		// Enable CORS so requests must be from Mendix app
		String origin = servletRequest.getHeader("Origin");
        if (origin != null && isAllowedOrigin(origin, servletRequest)) {
            servletResponse.setHeader("Access-Control-Allow-Origin", origin);
            servletResponse.setHeader("Access-Control-Allow-Credentials", "true");
        }
		servletResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        servletResponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");	
		
		// Handle GET requests
        if ("GET".equals(servletRequest.getMethod())) {
            servletResponse.setStatus(HttpServletResponse.SC_OK);
            servletResponse.setContentType("application/json");
            try (OutputStream out = servletResponse.getOutputStream()) {
                String json = "{\"status\": \"ok\", \"proxy\": \"active\"}";
                out.write(json.getBytes("UTF-8"));
                out.flush();
             }
            servletResponse.flushBuffer();
            return;
        }
		
		HttpURLConnection conn = null;
		try {
			URL url = new URL(endpoint);
			conn = (HttpURLConnection) url.openConnection();
		
			conn.setConnectTimeout(5000); // Time to establish connection ms
			conn.setReadTimeout(60000); // Time to read stream ms
	
			conn.setRequestMethod(servletRequest.getMethod()); // e.g. POST
			conn.setRequestProperty("Content-Type", "application/json");
			if (apiType == openaioutputstream.proxies.ENUM_AI_Type.AzureOpenAI)  conn.setRequestProperty("api-key", apiKey);
			
			else if (apiType == openaioutputstream.proxies.ENUM_AI_Type.Anthropic) {
				conn.setRequestProperty("x-api-key", apiKey);
				conn.setRequestProperty("anthropic-version", "2023-06-01");
				//conn.setRequestProperty("anthropic-beta", "interleaved-thinking-2025-05-14"); // Extended thinking
			}	
			else conn.setRequestProperty("Authorization", "Bearer " + apiKey); // Default formatting for OpenAI
			conn.setRequestProperty("Accept", "text/event-stream, application/json");
			conn.setDoOutput(true);
			
			// Stream the outbound request to not buffering it all in memory
			conn.setChunkedStreamingMode(8192);
			
			int MAX_BODY_BYTES = 1024 * maxRequestSize;
			int totalBytes = 0;
			boolean tooLarge = false;
			
			
			try (InputStream requestStream = servletRequest.getInputStream();
			OutputStream upstreamOut = conn.getOutputStream()) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				
				while ((bytesRead = requestStream.read(buffer)) != -1) {
					totalBytes += bytesRead;
					if (totalBytes > MAX_BODY_BYTES) {
						tooLarge = true;
						break;
					}
					upstreamOut.write(buffer, 0, bytesRead);
					upstreamOut.flush();
				}
			}
			
			// Request exceeds maxiumum allowed size
			if (tooLarge) {
				Core.getLogger("OpenAIStream").warn("Request body too big.");
				servletResponse.setStatus(413);
				servletResponse.setContentType("application/json");
				try (OutputStream out = servletResponse.getOutputStream()) {
					String json = "{\"error\": \"Request body exceeds maximum allowed size.\"}";
					out.write(json.getBytes("UTF-8"));
					out.flush();
				}
				servletResponse.flushBuffer();
				return;
			}
			
			int responseCode = conn.getResponseCode(); // Upstream (AI provider) response
			servletResponse.setStatus(responseCode);
			
			InputStream responseStream = (responseCode >= 200 && responseCode < 300) 
						? conn.getInputStream() 
						: conn.getErrorStream();
			
			if (responseStream != null) {
				try (InputStream upstreamIn = responseStream;
				OutputStream clientOutput = servletResponse.getOutputStream()) {
					
					byte[] bufferRes = new byte[8192];
					int bytesReadRes;
					while ((bytesReadRes = upstreamIn.read(bufferRes)) != -1) {
						clientOutput.write(bufferRes, 0, bytesReadRes);
						clientOutput.flush();
					}
				}
				servletResponse.flushBuffer();
			}
		} catch (Exception e) {
			Core.getLogger("OpenAIStream").error("Proxy request to AI endpoint failed.", e);
			if (!servletResponse.isCommitted()) {
				servletResponse.setStatus(502);
				servletResponse.setContentType("application/json");
				try (OutputStream out = servletResponse.getOutputStream()) {
					String json = "{\"error\": \"AI request failed.\"}";
					out.write(json.getBytes("UTF-8"));
					out.flush();
				}
				servletResponse.flushBuffer();
			}
		}  finally {
			if (conn != null) conn.disconnect();
		}
	}
	
	// Only allows CORS for the app's own origin
	// This will need to be modified if a whitelist of origins is desired
	private boolean isAllowedOrigin(String origin, HttpServletRequest servletRequest) {
		String scheme = servletRequest.getScheme();
		String host = servletRequest.getServerName();
		int port = servletRequest.getServerPort();

		boolean isDefaultPort = (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443));
		String selfOrigin = scheme + "://" + host + (isDefaultPort ? "" : ":" + port);

		return origin.equalsIgnoreCase(selfOrigin);
	}
	
	
	
}