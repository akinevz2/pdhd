package ac.uk.sussex.kn253.resources;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import ac.uk.sussex.kn253.AiToolCallException;
import ac.uk.sussex.kn253.AiToolsFailureException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public class ExceptionMappers {

    private static final String ERROR_KIND_AI_LAYER = "AI_LAYER_FAILURE";
    private static final String ERROR_KIND_SERVICE_LAYER = "SERVICE_LAYER_FAILURE";

    public record ErrorBody(String errorKind, String detail) {
    }

    @ServerExceptionMapper
    public Response mapAiToolCallException(final AiToolCallException ex) {
        ErrorBody body = new ErrorBody(ERROR_KIND_AI_LAYER, ex.getMessage());
        return Response.status(502)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    @ServerExceptionMapper
    public Response mapAiToolsFailureException(final AiToolsFailureException ex) {
        ErrorBody body = new ErrorBody(ERROR_KIND_SERVICE_LAYER, ex.getMessage());
        return Response.status(500)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
