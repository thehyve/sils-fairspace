package io.fairspace.saturn.services.metadata;

import io.fairspace.saturn.auth.AuthorizationResult;
import io.fairspace.saturn.auth.AuthorizationVerifier;
import io.fairspace.saturn.auth.ForbiddenException;
import io.fairspace.saturn.auth.UserInfo;
import io.fairspace.saturn.services.PayloadParsingException;
import io.fairspace.saturn.services.metadata.validation.ValidationException;
import io.fairspace.saturn.util.UnsupportedMediaTypeException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import spark.servlet.SparkApplication;

import java.util.List;

import static io.fairspace.saturn.services.ModelUtils.*;
import static io.fairspace.saturn.services.errors.ErrorHelper.errorBody;
import static io.fairspace.saturn.services.errors.ErrorHelper.exceptionHandler;
import static io.fairspace.saturn.util.ValidationUtils.validateContentType;
import static javax.servlet.http.HttpServletResponse.*;
import static org.apache.jena.riot.RDFFormat.JSONLD;
import static org.eclipse.jetty.http.MimeTypes.Type.APPLICATION_JSON;
import static spark.Spark.*;

@AllArgsConstructor
@Slf4j
public class MetadataApp implements SparkApplication {
    private final String basePath;
    private final MetadataService api;
    private final AuthorizationVerifier authorizationVerifier;

    @Override
    public void init() {
        before(basePath + "/*", (request, response) -> {
            if(authorizationVerifier != null) {
                AuthorizationResult authorizationResult = authorizationVerifier.verify(request);
                if(!authorizationResult.isAuthorized()) {
                    log.warn("Authorization failed {} {}", request.requestMethod(), request.uri());
                    response.type(APPLICATION_JSON.asString());
                    halt(SC_FORBIDDEN, errorBody(SC_FORBIDDEN, authorizationResult.getMessage()));
                }
            }
        });
        path(basePath, () -> {
            get("/", JSON_LD_HEADER_STRING, (req, res) -> {
                res.type(JSON_LD_HEADER_STRING);
                return toJsonLD(api.get(
                        req.queryParams("subject"),
                        req.queryParams("predicate"),
                        req.queryParams("object"),
                        req.queryParams().contains("labels")));
            });
            get("/entities/", JSON_LD_HEADER_STRING, (req, res) -> {
                res.type(JSONLD.getLang().getHeaderString());
                return toJsonLD(api.getByType(req.queryParams("type")));
            });
            put("/", (req, res) -> {
                validateContentType(req, JSON_LD_HEADER_STRING);
                api.put(fromJsonLD(req.body()));
                return "";
            });
            patch("/", (req, res) -> {
                validateContentType(req, JSON_LD_HEADER_STRING);
                api.patch(fromJsonLD(req.body()));
                return "";
            });
            delete("/", (req, res) -> {
                if (JSON_LD_HEADER_STRING.equals(req.contentType())) {
                    api.delete(fromJsonLD(req.body()));
                } else {
                    api.delete(req.queryParams("subject"), req.queryParams("predicate"), req.queryParams("object"));
                }
                return "";
            });
            notFound((req, res) -> errorBody(SC_NOT_FOUND, "Not found"));
            exception(PayloadParsingException.class, exceptionHandler(SC_BAD_REQUEST, "Malformed request body"));
            exception(UnsupportedMediaTypeException.class, exceptionHandler(SC_UNSUPPORTED_MEDIA_TYPE, null));
            exception(ValidationException.class, exceptionHandler(SC_BAD_REQUEST, null));
            exception(ForbiddenException.class, exceptionHandler(SC_FORBIDDEN, null));
            exception(IllegalArgumentException.class, exceptionHandler(SC_BAD_REQUEST, null));
        });
    }

}
