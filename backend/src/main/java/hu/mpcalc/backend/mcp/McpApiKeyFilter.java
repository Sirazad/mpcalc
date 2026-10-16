package hu.mpcalc.backend.mcp;

import hu.mpcalc.backend.engine.EngineJson;
import hu.mpcalc.backend.service.MessageService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The MCP endpoint is for the chat service only: it needs the header X-API-Key to equal MCP_API_KEY. Fails closed: if no key is
 * configured the endpoint refuses every call. Other paths (the REST API of the page) are not touched.
 */
@Component
public class McpApiKeyFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-API-Key";
    private static final Logger LOG = LoggerFactory.getLogger(McpApiKeyFilter.class);

    private final String apiKey;
    private final String endpoint;
    private final MessageService messages;
    private final LocaleResolver locales;

    public McpApiKeyFilter(@Value("${mpcalc.mcp-api-key:}") String apiKey,
                           @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}") String endpoint,
                           MessageService messages, LocaleResolver locales) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.endpoint = endpoint;
        this.messages = messages;
        this.locales = locales;
        if (this.apiKey.isBlank()) LOG.warn("MCP_API_KEY is not set: the MCP endpoint {} refuses every call", endpoint);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals(endpoint) || path.startsWith(endpoint + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (apiKey.isBlank()) { refuse(request, response, 503, "api.mcp.notConfigured"); return; }
        String given = request.getHeader(HEADER);
        boolean ok = given != null && MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), apiKey.getBytes(StandardCharsets.UTF_8));
        if (!ok) { refuse(request, response, 401, "api.mcp.keyRequired"); return; }
        chain.doFilter(request, response);
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response, int status, String key) throws IOException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", messages.text(locales.resolveLocale(request), key));
        body.put("code", key);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(EngineJson.write(body));
    }
}
