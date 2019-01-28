package io.fairspace.saturn.rdf.transactions;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public class SimpleTransactionSerializer implements TransactionSerializer {
    public static final TransactionSerializer INSTANCE = new SimpleTransactionSerializer();

    private SimpleTransactionSerializer() {
    }

    @Override
    public void write(TransactionRecord transaction, OutputStream out) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(transaction);
        }
    }
}
