package io.fairspace.saturn.rdf.transactions;

import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.sparql.modify.request.QuadDataAcc;
import org.apache.jena.sparql.modify.request.UpdateDataDelete;
import org.apache.jena.sparql.modify.request.UpdateDataInsert;
import org.apache.jena.update.UpdateRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class SparqlTransactionSerializer implements TransactionSerializer {
    static final String TIMESTAMP_PREFIX = "# Timestamp: ";
    static final String USER_NAME_PREFIX = "# User Name: ";
    static final String USER_ID_PREFIX = "# User ID: ";
    static final String COMMIT_MESSAGE_PREFIX = "# Commit Message: ";

    public static final TransactionSerializer INSTANCE = new SparqlTransactionSerializer();

    private SparqlTransactionSerializer() {
    }

    @Override
    public void write(TransactionRecord transaction, OutputStream out) throws IOException {
        IndentedWriter writer = new IndentedWriter(out);

        writer.write(TIMESTAMP_PREFIX + transaction.getTimestamp() + "\n") ;
        if (transaction.getUserName() != null) {
            writer.write(USER_NAME_PREFIX + transaction.getUserName() + "\n");
        }
        if (transaction.getUserId() != null) {
            writer.write(USER_ID_PREFIX + transaction.getUserId() + "\n");
        }
        if (transaction.getCommitMessage() != null) {
            writer.write(COMMIT_MESSAGE_PREFIX + transaction.getCommitMessage().replace('\n', ' ') + "\n");
        }
        writer.write('\n');

        UpdateDataDelete updateDataDelete = new UpdateDataDelete(new QuadDataAcc(new ArrayList<>(transaction.getDeleted())));
        UpdateDataInsert updateDataInsert = new UpdateDataInsert(new QuadDataAcc(new ArrayList<>(transaction.getAdded())));

        new UpdateRequest().add(updateDataDelete).add(updateDataInsert).output(writer);
    }
}
