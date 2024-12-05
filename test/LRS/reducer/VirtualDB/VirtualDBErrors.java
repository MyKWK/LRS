package LRS.reducer.VirtualDB;

import LRS.common.query.ExpectedErrors;

public final class VirtualDBErrors {
    public VirtualDBErrors() {
    }

    public static void addErrors(ExpectedErrors errors) {
        errors.add("Default error");
    }
}
