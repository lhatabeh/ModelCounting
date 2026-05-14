# CNF and d-DNNF Model Counter

A Java tool for counting the number of satisfying models of a CNF formula using two approaches:

1. **DPLL-based #SAT** — a custom DPLL solver with unit propagation, free variable shortcutting, and a most-frequent variable heuristic
2. **d-DNNF compilation** — compiles the formula into a Deterministic Decomposable Negation Normal Form and counts models in polynomial time using the [LogicNG](https://logicng.org/) library

## Requirements

- Java 17+
- Maven

## Build

```bash
mvn package
```

## Usage

```bash
java -cp target/PlanningProject-1.0-SNAPSHOT.jar org.example.Main <path-to-cnf-file>
```

The input file must be in [DIMACS CNF format](https://people.sc.fsu.edu/~jburkardt/data/cnf/cnf.html).

## Example Output

```
Loaded CNF: 10 variables, 20 clauses.
Time spent for CNF model count: 42 ms (0.042 s)
Number of models (CNF): 512
Time spent for compiled d-DNNF: 15 ms (0.015 s)
Loaded d-DNNF: 10 variables, 38 nodes
Time spent for d-DNNF model count: 3 ms (0.003 s)
Number of models (d-DNNF): 512
```

## Dependencies

- [LogicNG 2.6.0](https://github.com/logic-ng/LogicNG) — formula representation, DIMACS parsing, and d-DNNF compilation
