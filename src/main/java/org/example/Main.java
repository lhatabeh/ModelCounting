package org.example;

import org.logicng.formulas.FormulaFactory;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {

        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <path-to-cnf-file>");
        }

        FormulaFactory f = new FormulaFactory();
        ModelCount mc = new ModelCount(f);

        mc.loadCNF(args[0]);
        mc.countCNF();
        mc.compileDnnf();
        mc.countDnnf();
    }
}
