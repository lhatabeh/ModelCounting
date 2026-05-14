package org.example;

import org.logicng.formulas.*;
import org.logicng.io.readers.DimacsReader;
import org.logicng.knowledgecompilation.dnnf.DnnfFactory;
import org.logicng.knowledgecompilation.dnnf.datastructures.Dnnf;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

public class ModelCount {

    private final FormulaFactory f;
    private List<Formula> clauses;
    private SortedSet<Variable> variables;
    private Dnnf dnnf;

    public ModelCount(FormulaFactory f) {

        this.f = f;
    }

    public void loadCNF(String fileURL) throws IOException {
        clauses = DimacsReader.readCNF(fileURL, f);
        variables = new TreeSet<>();
        for (Formula clause : clauses) {
            variables.addAll(clause.variables());
        }
        System.out.println("Loaded CNF: " + variables.size() + " variables, " + clauses.size() + " clauses.");
    }

    // CNF model counting via DPLL-based SAT

    public BigInteger countCNF() {
        long t0 = System.currentTimeMillis();
        BigInteger count = dpll(new ArrayList<>(clauses), new ArrayList<>(variables));
        long elapsedCNF = System.currentTimeMillis() - t0;
        System.out.println("Time spent for CNF model count: " + elapsedCNF +
                " ms (" + elapsedCNF / 1000.0 + " s)");
        System.out.println("Number of models (CNF): " + count);

        return count;
    }

    private BigInteger dpll(List<Formula> clauses, List<Variable> unassigned) {
        List<Formula> active = simplify(clauses);
        if (active == null) return BigInteger.ZERO;
        if (active.isEmpty()) return BigInteger.TWO.pow(unassigned.size());

        List<Variable> free = new ArrayList<>(unassigned);

        // Unit propagation
        boolean changed;
        do {
            changed = false;
            for (Formula clause : active) {

                Literal unit = (clause.type() == FType.LITERAL) ? (Literal) clause : null;

                Variable v = (unit != null) ? unit.variable() : null;

                if (free.remove(v)) {
                    active = simplify(assign(active, v, unit.phase()));
                    if (active == null) return BigInteger.ZERO;
                    if (active.isEmpty()) return BigInteger.TWO.pow(free.size());
                    changed = true;
                    break;
                }

            }
        } while (changed);

        if (free.isEmpty()) return BigInteger.ZERO;


        Set<Variable> activeVars = new HashSet<>();
        for (Formula clause : active) activeVars.addAll(clause.variables());

        List<Variable> constrained = new ArrayList<>();
        int extraFree = 0;
        for (Variable v : free) {
            if (activeVars.contains(v)) constrained.add(v);
            else extraFree++;
        }

        if (constrained.isEmpty()) return BigInteger.TWO.pow(extraFree);


        Variable v = mostFrequent(active, constrained);
        List<Variable> rest = new ArrayList<>(constrained);
        rest.remove(v);

        BigInteger count = dpll(assign(active, v, true), rest)
                .add(dpll(assign(active, v, false), rest));
        return extraFree > 0 ? count.multiply(BigInteger.TWO.pow(extraFree)) : count;
    }

    private List<Formula> simplify(List<Formula> clauses) {
        if (clauses == null) return null;
        List<Formula> active = new ArrayList<>(clauses.size());
        for (Formula c : clauses) {
            if (c.type() == FType.FALSE) return null;
            if (c.type() != FType.TRUE) active.add(c);
        }
        return active;
    }

    private Variable mostFrequent(List<Formula> active, List<Variable> vars) {
        Map<Variable, Integer> freq = new HashMap<>();
        for (Formula clause : active)
            for (Variable var : clause.variables())
                freq.merge(var, 1, Integer::sum);
        Variable best = vars.get(0);
        int bestCount = freq.getOrDefault(best, 0);
        for (Variable v : vars) {
            int c = freq.getOrDefault(v, 0);
            if (c > bestCount) {
                best = v;
                bestCount = c;
            }
        }
        return best;
    }

    private List<Formula> assign(List<Formula> clauses, Variable v, boolean value) {
        List<Formula> result = new ArrayList<>(clauses.size());
        for (Formula clause : clauses) {
            result.add(simplifyClause(clause, v, value));
        }
        return result;
    }

    private Formula simplifyClause(Formula clause, Variable v, boolean value) {
        if (clause.type() == FType.LITERAL) {
            Literal lit = (Literal) clause;
            if (lit.variable().equals(v)) {
                return (lit.phase() == value) ? f.verum() : f.falsum();
            }
            return clause;
        }
        // OR clause (possibly nested)
        List<Formula> remaining = new ArrayList<>();
        for (Formula operand : clause) {
            Formula simplified = simplifyClause(operand, v, value);
            if (simplified.type() == FType.TRUE) return f.verum();
            if (simplified.type() != FType.FALSE) remaining.add(simplified);
        }
        if (remaining.isEmpty()) return f.falsum();
        return remaining.size() == 1 ? remaining.get(0) : f.or(remaining);
    }

    //d-DNNF compilation and model counting

    public void compileDnnf() {
        long t2 = System.currentTimeMillis();
        dnnf = new DnnfFactory().compile(f.and(clauses));
        long elapsedDnnf = System.currentTimeMillis() - t2;
        System.out.println("Time spent for compiled d-DNNF: " + elapsedDnnf +
                " ms (" + elapsedDnnf / 1000.0 + " s)");
    }

    public BigInteger countDnnf() {
        long t1 = System.currentTimeMillis();
        Set<Variable> allVars = dnnf.formula().variables();
        BigInteger count = countNode(dnnf.formula(), allVars);
        long elapsedCount = System.currentTimeMillis() - t1;
        System.out.println("Time spent for d-DNNF model count: " + elapsedCount +
                " ms (" + elapsedCount / 1000.0 + " s)");
        System.out.println("Number of models (d-DNNF): " + count);
        return count;
    }

    private BigInteger countNode(Formula node, Set<Variable> scope) {
        switch (node.type()) {
            case TRUE:
                return BigInteger.TWO.pow(scope.size());
            case FALSE:
                return BigInteger.ZERO;
            case LITERAL:
                return BigInteger.TWO.pow(scope.size() - 1);
            case AND: {
                // Decomposable
                BigInteger result = BigInteger.ONE;
                for (Formula child : node) {
                    result = result.multiply(countNode(child, child.variables()));
                }
                return result;
            }
            case OR: {
                // Deterministic
                BigInteger result = BigInteger.ZERO;
                for (Formula child : node) {
                    Set<Variable> childVars = child.variables();
                    int freeVars = scope.size() - childVars.size();
                    BigInteger childCount = countNode(child, childVars)
                            .multiply(BigInteger.TWO.pow(freeVars));
                    result = result.add(childCount);
                }
                return result;
            }
            default:
                throw new IllegalStateException("Unexpected node type in d-DNNF: " + node.type());
        }
    }
}
