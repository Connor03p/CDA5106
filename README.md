# CDA5106

# Machine Problem:
## To Compile:
`javac Main.java`

## To Run:
`java Main <scheduling queue size> <fetch size> <path to trace file>`

# Batching Optimization:
## To Compile:
`javac ./batching/*.java`

## To Run:
`java batching.Batch <scheduling queue size> <fetch size> <path to trace file>`

## What happens?
Once it excutes, it should print messages on the execution, before finally writing to a text file the ouput

## How Does Batch.java work?
The main purpose of this file is to group (batch) instructions for "issue" based on their dependencies. Ideally this would optimize instruction throughput while respecting different data hazards like RAW, WAR, and WAW dependencies.The main "scheduleBatches" function processes the list of instructions from the input file and returns a list of instruction batches.Each batch represents the set of instructions that can possibly be issued in the same cycle. A dependency graph is created using instruction tags and are based on all three dependency types. Instructions with no remaining dependencies are eligible to be scheduled.If a cycle in the dependency graph prevents any instruction from being scheduled (it has unresolved dependencies), the algorithm forces an instruction to be issued to break the cycle and proceed.Our goal with this batching strategy is to help identify different reordering strategies while maintaining correctness and reducing overall clock cycles. 
