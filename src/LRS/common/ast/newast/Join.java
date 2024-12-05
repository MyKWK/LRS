package LRS.common.ast.newast;

import LRS.common.schema.AbstractTable;
import LRS.common.schema.AbstractTableColumn;

public interface Join<E extends Expression<C>, T extends AbstractTable<C, ?, ?>, C extends AbstractTableColumn<?, ?>>
        extends Expression<C> {

    Expression<C> getOnClause();

    void setOnClause(E onClause);
}
