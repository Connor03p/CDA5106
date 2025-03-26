import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;

enum State {
    IF,
    ID,
    IS,
    EX,
    WB;
}

enum Reg {
    free,
    write,
}


public class Main {

    // Variables chosen by user
    static int schedulingQueueSize = 1;
    static int fetchRate = 1;
    static String filename = "./traces/val_trace_gcc.txt";

    public static void main(String args[]) {
        /*
            Scanner inputScanner = new Scanner(System.in);
            System.out.println("Enter trace file:");
            String filename = inputScanner.nextLine();
            inputScanner.close();
        */
        
        try {
            schedulingQueueSize = Integer.parseInt(args[0]);
            fetchRate = Integer.parseInt(args[1]);
            filename = args[2];
            
        } catch (Exception e) {
            System.out.println("An error has occured, arguments potentially incorrect. Did you format it as: 'java Main <scheduleQueueSize> <fetchRate> <tracefile>'?");
            return;
        }


        File file = new File(filename);
        Scanner fileScanner;
        int tagNum = 0;
        
        try {
            fileScanner = new Scanner(file);

            while (fileScanner.hasNextLine()) {
                String line = fileScanner.nextLine();
                String[] split = line.split(" ");
                Instruction newInstruction = new Instruction(
                    Integer.parseInt(split[0], 16), // Converts hexadecimal to int
                    Integer.parseInt(split[1]), 
                    Integer.parseInt(split[2]), 
                    Integer.parseInt(split[3]), 
                    Integer.parseInt(split[4]),
                    tagNum
                );
                tagNum++;
                System.out.println(newInstruction.toString());
                instructions.add(newInstruction);
            }

            fileScanner.close();
        } catch (FileNotFoundException e) {
            System.out.println("Error reading file.");
            return;
        }        

        // Main Simulator Loop
        Iterator<Instruction> iterator = instructions.iterator();
        int instructionsProcessed = 0;
        Arrays.fill(register, Reg.free);
        do  {
            fakeRetire();
            execute();
            issue();
            dispatch();
            
            for (int i = 0; i < fetchRate; i++) {
                if (iterator.hasNext()) {
                    fetch(iterator.next());
                    instructionsProcessed++;
                }
            }
        }
        while(advanceCycle(instructionsProcessed));

        System.out.println("Writing output to file...");
        writeOutput(tagNum);
        System.out.println("Done!");
    }

    private static void writeOutput(int tagNum) {
        // Data to be written in file
        String text = "";

        // Defining the file name of the file
        String name = filename.substring(filename.lastIndexOf('_') + 1);
        name = name.substring(0, name.lastIndexOf('.'));

        Path fileName = Path.of("./pipe_" + schedulingQueueSize + "_" + fetchRate + "_" + name + ".txt");

        Iterator<Instruction> iterator = instructions.iterator();
        while (iterator.hasNext())
        {
            Instruction i = iterator.next();
            text += i + "\n";
        }

        text += "number of instructions = " + (tagNum) + "\n";
        text += "number of cycles       = " + (PC) + "\n";
        text += "IPC                    = " + (PC/tagNum);

        try {
            Files.writeString(fileName, text);
        } 
          catch (IOException e) {
            System.err.println("An error occurred: " + e.getMessage());
        }
    }

    private static int PC = 0;

    /**
     * This is a data structure that represents the trace file, holds all incoming instructions 
     */
    private static List<Instruction> instructions = new ArrayList<>();

    /**
     * 
     */
    private static Queue<Instruction> fakeROB = new LinkedList<>();

    /**
        This contains a list of instructions in either the IF or ID
        state. The dispatch_list models the Dispatch Queue. (By including both
        the IF and ID states, we don’t need to separately model the pipeline
        registers of the fetch and dispatch stages.)
    */
    private static List<Instruction> dispatchList = new ArrayList<>();

    /**
        This contains a list of instructions in the IS state (waiting for
        operands or available issue bandwidth). The issue_list models the
        Scheduling Queue.
    */
    private static List<Instruction> issueList = new ArrayList<>();

    /**
        This contains a list of instructions in the EX state (waiting
        for the execution latency of the operation). The execute_list models
        the FUs.
    */
    private static List<Instruction> executeList = new ArrayList<>();

    /*
     *  This is the array that will simulate the 'register', its 127 in size since the
     *  current instruction set specifies instructions will only registers 0 - 127
     *  -1 is no reg, so do nothing with that
     * 
     *  set to null if unoccupied
     */
    private static Reg[] register = new Reg[127];

    /**
     * @param List<Instruction> instructions
     * @return boolean - If the list is empty, return false and stop execution
     */
        
    private static boolean advanceCycle(int instructionsProcessed) {
        // Advance simulator cycle?
        PC += 1;

        // Check if Instruction List is empty, return false
        return !(instructionsProcessed >= instructions.size() && fakeROB.size() <= 0);

    }

    /**
     * IF Stage:
     * Should do the following:
     * 1. Push new instruction from instruction list to fake-ROB. Initialize the instruction's data structure
     *    including setting its state to IF.
     * 2. Add the instruction to the dispatch_list and reserve a dispatch queue entry 
     *    (increment count of the number of instruction in the dispatch queue)
     * 
     * @param Instruction instruction - an instruction to be configured, and added to dispatch_list
     * 
     */
    private static void fetch(Instruction instruction) {
        System.out.println("Fetch");
        // Here, we are setting the instruction to IF, not sure what the initializing the data structure is (besides creating it?);
        instruction.setState(State.IF);
        instruction.c_IF = PC;
        
        // Push instruction to fake ROB
        fakeROB.add(instruction);

        // Push to dispatch_list, dispatchList.size() should give us the count of this arraylist.
        dispatchList.add(instruction);

        System.out.println("  Fetched instruction: " + instruction);
    }

    /**
     * ID Stage:
     * Should do the following:
     * From dispatch_list, construct a temp list of instructions in the ID state 
     * 
     * Scan the temp list in ascending order of tags and if the scheduling queue is not full, then:
     * 1. Remove the instruction from the dispatch_list and add it to the issue_list. Reserve a schedule
     *    queue entry (e.g. increment a count of the number of instructions in the scheduling queue) and
     *    free a dispatch queue entry (e.g. decrement a count of the number of instructions in the dispatch queue)
     * 2. Transition from the ID state to the IS state
     * 3. Rename source operands by lookingg up state in the register file:
     * 
     */
    private static void dispatch() {
        System.out.println("Dispatch");
        System.out.println("  " + dispatchList.size() + " instructions in list");

        Iterator<Instruction> iterator = dispatchList.iterator();


        while (iterator.hasNext()) {
            Instruction i = iterator.next();

            // Only add instructions with 'ID' tag to new list
            // @NOTE - I'm assuming the 1 cycle stall is it starts at IF, otherwise start at 'ID'
            if (i.state == State.ID) {
                boolean isAssigned = false;
            
                // Instruction doesn't need a register? Simply proceed
                if (i.src1 == -1 && i.src2 == -1) {
                    isAssigned = true;
                }
                // Instruction is using 1 register, indicated by -1
                else if (i.src2 == -1 && register[i.src1] != Reg.write) {
                    isAssigned = true;
                }
                else if (i.src1 == -1 && register[i.src2] != Reg.write) {
                    isAssigned = true;
                }
                // Instruction is using two registers
                else if (register[i.src1] != Reg.write && register[i.src2] != Reg.write) {
                    isAssigned = true;
                }
                
                // If an instruction's source registers are ready, we can proceed to be issued
                if (isAssigned && issueList.size() < schedulingQueueSize) {
                    System.out.println("  Instruction " + i.tag + " moved to issueList.");
                    i.setState(State.IS);

                    i.d_ID = PC - i.c_ID;
                    i.c_IS = PC;

                    iterator.remove();
                    issueList.add(i); // automatically increments num in scheduling queue
                }
                else {
                    // System.out.println("  Instruction " + i.tag + " not ready, source is being written to or scheduling queue is full");
                }
            }
            else {
                i.setState(State.ID);
                i.d_IF = PC - i.c_IF;
                i.c_ID = PC;
            }
        }
    }

    /**
     * IS Stage:
     *  From the issue_list, construct a temp list of instructions whose operands are ready – these are the READY instructions.
     * Scan the READY instructions in ascending order of tags and issue up to N+1 of them. To issue an instruction:
     *  1) Remove the instruction from the issue_list and add it to the execute_list.
     *  2) Transition from the IS state to the EX state.
     *  3) Free up the scheduling queue entry (e.g., decrement a count of the number of instructions in the scheduling queue)
     *  4) Set a timer in the instruction’s data structure that will allow you to model the execution latency 
     */
    private static void issue() {
        System.out.println("Issue");
        System.out.println("  " + issueList.size() + " instructions in list");

        // Issue instructions first based on their tag
        Collections.sort(issueList);

        // Uses iterator to prevent concurrent modification exception
        Iterator<Instruction> iterator = issueList.iterator();

        while (iterator.hasNext()) {
            Instruction i = iterator.next();
            
            i.setState(State.EX);
            i.d_IS = PC - i.c_IS;
            i.c_EX = PC;
            
            // This register is now being written to.
            if (i.dest != -1) {
                register[i.dest] = Reg.write;
            }

            executeList.add(i);
            iterator.remove(); // removes item from scheduling queue, decrementing value 
        }
    }

    /**
     * EX Stage:
     * Should do the following:
     *  1. Remove instruction from execute_list
     *  2. Transition from EX state to WB state
     *  3. Update register file state and wake up dependant instructions (set operand ready flags)
     */
    private static void execute() {
        System.out.println("Execute");
        System.out.println("  " + executeList.size() + " instructions in list");
        Iterator<Instruction> iterator = executeList.iterator();
        while (iterator.hasNext()) {
            Instruction i = iterator.next();
            
            // Increment timer to simulate execution latency
            if (i.exeTimer < i.latency) {
                i.exeTimer++; 
                System.out.println("  Instruction " + i.tag + " timer: " + i.exeTimer + "/" + i.latency);
            }
               
            // When the timer finishes, move to WB state
            else {
                i.setState(State.WB);

                i.d_EX = PC - i.c_EX;
                i.c_WB = PC;

                System.out.println("  Instruction " + i + " done executing.");
                
                if (i.dest != -1)
                    register[i.dest] = Reg.free;
                
                iterator.remove();
            }       
        }
    }
    /**
     * Removes instructions from the head of the fake-ROB
     * until an instruction is reached that is not in the WB state
     */
    private static void fakeRetire() {
        System.out.println("FakeRetire");


        while (true) { 
            if (!fakeROB.isEmpty() && fakeROB.peek().state == State.WB) {
                Instruction i = fakeROB.remove();
                i.d_WB = PC - i.c_WB;
            }
            else {
                break;
            }
        }
    }
}

class Instruction implements Comparable<Instruction> {
    int pc, op, dest, src1, src2, tag;

    // Store cycle and duration instruction was in each state for final output
    int c_IF = -1, d_IF = -1;
    int c_ID = -1, d_ID = -1;
    int c_IS = -1, d_IS = -1;
    int c_EX = -1, d_EX = -1;
    int c_WB = -1, d_WB = -1;

    State state;

    int latency;
    int exeTimer = 0;

    Instruction(int pc, int op, int dest, int src1, int src2, int tag) {
        this.pc = pc;
        this.op = op;
        this.dest = dest;
        this.src1 = src1;
        this.src2 = src2;
        this.state = null;
        this.tag = tag;
        setLatency();
    }

    // <tag> fu{<operation type>} src{<src1 reg#>,<src2 reg#>} dst{<dest reg#>} IF{<cycle>,<duration>} ID{<cycle>,<duration>} IS{<cycle>,<duration>} EX{<cycle>,<duration>} WB{<cycle>,<duration>} 
    @Override
    public String toString() {
        return tag + " "
            + "fu{" + op + "} "
            + "src{" + src1 + ", " + src2 + "} "
            + "dst{" + dest + "} "
            + "IF{" + c_IF + ", " + d_IF + "} "
            + "ID{" + c_ID + ", " + d_ID + "} "
            + "IS{" + c_IS + ", " + d_IS + "} "
            + "EX{" + c_EX + ", " + d_EX + "} "
            + "WB{" + c_WB + ", " + d_WB + "} ";
    }

    public void setState(State state) {
        this.state = state;
    }

    // Implement the compareTo method for sorting
    @Override
    public int compareTo(Instruction other) {
        return Integer.compare(this.tag, other.tag); // Sort by pc, ascending order

    }

    /**
     * Set how many cycles this instruction needs in order to finish processing based on
     * op code: 0 => 1 cycle, 1 => 2 cycles, 2 => 5 cycles.
     */
    private void setLatency() {
        switch (this.op) {
            case 0:
                this.latency = 1;
                break;
            case 1:
                this.latency = 2;
                break;
            case 2:
                this.latency = 5;
                break;
            default:
                break;
        }
    }
}