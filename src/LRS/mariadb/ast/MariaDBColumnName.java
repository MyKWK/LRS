package LRS.mariadb.ast;

import LRS.mariadb.MariaDBSchema.MariaDBColumn;

public class MariaDBColumnName implements MariaDBExpression {

    private final MariaDBColumn column;

    public MariaDBColumnName(MariaDBColumn column) {
        this.column = column;
    }

    public MariaDBColumn getColumn() {
        return column;
    }

}
