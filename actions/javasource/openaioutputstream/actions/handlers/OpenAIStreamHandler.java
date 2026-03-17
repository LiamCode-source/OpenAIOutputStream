package ex.actions.handlers;

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
    private final String model;

    public OpenAIStreamHandler(String apiKey, String endpoint, String model) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.model = model;
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
        if (origin != null) {
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
		
		
		
		
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();


        conn.setRequestMethod(servletRequest.getMethod()); // e.g. POST
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setDoOutput(true);
		
		
		InputStream openaiStream = servletRequest.getInputStream();
		OutputStream clientStream = conn.getOutputStream();
		
		
		byte[] buffer = new byte[1024];
        int bytesRead;
		
		
		while ((bytesRead = openaiStream.read(buffer)) != -1) {
            clientStream.write(buffer, 0, bytesRead);
            clientStream.flush();
        }
		
		int responseCode = conn.getResponseCode(); // OpenAI response
        servletResponse.setStatus(responseCode);
		
		InputStream responseStream = conn.getInputStream(); 
		
        if (responseStream != null) {
			try (OutputStream clientOutput = servletResponse.getOutputStream()) {
				byte[] bufferRes = new byte[1024];
                int bytesReadRes;
                while ((bytesReadRes = responseStream.read(bufferRes)) != -1) {
                    clientOutput.write(bufferRes, 0, bytesReadRes);
                    clientOutput.flush();
                }
            }
            servletResponse.flushBuffer();
        }


        /*conn.getOutputStream().write(body.getBytes());

        //InputStream openaiStream = conn.getInputStream();
		
        response.setContentType("text/event-stream");
        response.addHeader("Cache-Control", "no-cache");

        //OutputStream clientStream = response.getOutputStream();*/


        //openaiStream.close();
        //clientStream.close();
    }
}