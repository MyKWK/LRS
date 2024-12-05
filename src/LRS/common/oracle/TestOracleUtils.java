package LRS.common.oracle;

import LRS.IgnoreMeException;
import LRS.Randomly;
import LRS.common.schema.AbstractSchema;
import LRS.common.schema.AbstractTable;
import LRS.common.schema.AbstractTableColumn;
import LRS.common.schema.AbstractTables;

public final class TestOracleUtils {

    private TestOracleUtils() {
    }

    public static <T extends AbstractTable<C, ?, ?>, C extends AbstractTableColumn<?, ?>> AbstractTables<T, C> getRandomTableNonEmptyTables(
            AbstractSchema<?, T> schema) {
        if (schema.getDatabaseTables().isEmpty()) {
            throw new IgnoreMeException();
        }
        return new AbstractTables<>(Randomly.nonEmptySubset(schema.getDatabaseTables()));
    }
}
