package io.fairspace.saturn.services.metadata;

import io.fairspace.saturn.services.BaseApp;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

import static io.fairspace.saturn.services.metadata.Serialization.getFormat;
import static io.fairspace.saturn.services.metadata.Serialization.serialize;
import static io.fairspace.saturn.vocabulary.Vocabularies.VOCABULARY;
import static org.eclipse.jetty.http.MimeTypes.Type.APPLICATION_JSON;
import static spark.Spark.get;

public class VocabularyApp extends BaseApp {
    public VocabularyApp(String basePath) {
        super(basePath);
    }

    private static class EntityTypeNode {
        public EntityTypeNode(String typeName) {
            this.TypeName = typeName;
        }

        public void addChild(EntityTypeNode childNode) {
            ChildNodes.add(childNode.TypeName);
        }

        @Getter public boolean IsRoot = false;
        @Getter public String TypeName;
        @Getter public ArrayList<String> ChildNodes = new ArrayList<>();
    }

    @Override
    protected void initApp() {
        get("/hierarchy/", (req, res) -> {
            var format = getFormat(req.headers("Accept"));
            res.type(format.getLang().getHeaderString());

            var root = new EntityTypeNode("Department");
            root.IsRoot = true;
            var pi = new EntityTypeNode("PI");
            var study = new EntityTypeNode("Study");
            var object = new EntityTypeNode("Object");

            root.addChild(pi);
            pi.addChild(study);
            study.addChild(object);

            var allNodes = new ArrayList<>(List.of(root, pi, study, object));

            res.type(APPLICATION_JSON.asString());
            return mapper.writeValueAsString(allNodes);
        });

        get("/", (req, res) -> {
            var format = getFormat(req.headers("Accept"));
            res.type(format.getLang().getHeaderString());
            return serialize(VOCABULARY, format);
        });
    }
}