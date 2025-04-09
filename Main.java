import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

public class Main {

    // Variables chosen by user
    static int schedulingQueueSize = 8;
    static int fetchRate = 8;
    static String filename = "./traces/val_trace_gcc.txt";

    public static void main(String args[]) {
        try {
            if (args.length != 0) {
                schedulingQueueSize = Integer.parseInt(args[0]);
                fetchRate = Integer.parseInt(args[1]);
                filename = args[2];
            }
            
        } catch (NumberFormatException e) {
            System.out.println("An error has occured, arguments potentially incorrect. Did you format it as: 'java Main <scheduleQueueSize> <fetchRate> <tracefile>'?");
            return;
        }

        File file = new File(filename);
        Scanner fileScanner;
        int tagNum = 0;
        List<Instruction> instructions = new ArrayList<>();
        
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


        Simulator.fetchRate = fetchRate;
        Simulator.dispatchSize = fetchRate * 2; // The Dispatch Queue is 2N instructions in size
        Simulator.issueSize = schedulingQueueSize;
        
        Simulator.dispatch_list = new ArrayList<Instruction>(Simulator.dispatchSize);
        Simulator.issue_list = new ArrayList<Instruction>(Simulator.issueSize);
        Simulator.execute_list = new ArrayList<Instruction>();

        Simulator.run(instructions);

        System.out.println("Finished! Writing to file...");

        // Data to be written in file
        String text = "";

        // Defining the file name of the file
        String name = filename.substring(filename.lastIndexOf('_') + 1);
        name = name.substring(0, name.lastIndexOf('.'));

        Path fileName = Path.of("./pipe_" + schedulingQueueSize + "_" + fetchRate + "_" + name + ".txt");

        for (Instruction i : instructions) {
            text += i + "\n";
        }

        text += "number of instructions = " + (tagNum) + "\n";
        text += "number of cycles       = " + (Simulator.PC) + "\n";
        text += "IPC                    = " + (float)(Simulator.PC) / (float)(tagNum);

        try {
            Files.writeString(fileName, text);
            System.out.println("Wrote to " + filename);
        } 
          catch (IOException e) {
            System.err.println("An error occurred: " + e.getMessage());
        }
    }
}

class Simulator {

    public static int fetchRate = 1;
    public static int issueSize = 1;
    public static int dispatchSize = 1;
    public static int PC = 0;

    /** Program iterator */
    private static Iterator<Instruction> iterator;

    /** A circular FIFO that holds all active instructions in their program order */
    public static CircularQueue FakeROB = new CircularQueue(1024);
 
    /** Contains all in IF or ID. Models the scheduling Queue */
    public static List<Instruction> dispatch_list;

    /** Contains all in IS. Models the scheduling Queue. */
    public static List<Instruction> issue_list;

    /** Contains all in EX. Models the scheduling Queue */
    public static List<Instruction> execute_list;

    /** Register File */
    public static int[] register = new int[127];

    public static void run(List<Instruction> instructions)
    {
        Simulator.iterator = instructions.iterator();
        Arrays.fill(register, -1);

        do  {
            fakeRetire();
            execute();
            issue();
            dispatch();
            fetch();
        }
        while(advanceCycle());
    }

    private static void fetch() {
        System.out.println("Fetch");
        for (int i = 0; i < fetchRate; i++) {
            
            if (!iterator.hasNext())
                return;
        
            if (dispatch_list.size() >= dispatchSize) {
                System.out.println("  Could not fetch: Dispatch list full");
                return;
            }

            Instruction instruction = iterator.next();

            // Push the new instruction onto the fake-ROB.
            FakeROB.enqueue(instruction);

            // Set instruction state to IF
            instruction.state = State.IF;
            instruction.c_IF = PC;

            // Add the instruction to the dispatch_list
            dispatch_list.add(instruction);

            System.out.println("  Fetched instruction " + instruction.tag);
        }  
    }

    private static void dispatch() {     
        System.out.println("Dispatch");

        // From the dispatch_list, construct a temp list of instructions in ID state
        List<Instruction> ID_instructions = new ArrayList<Instruction>();
        for(Instruction i : dispatch_list)
            if(i.state == State.ID)
                ID_instructions.add(i);
        
        // Scan the temp list in ascending order of tags
        Collections.sort(ID_instructions, (i1, i2) -> i1.tag - i2.tag);
        Iterator<Instruction> ID_itr = ID_instructions.iterator();
        while (ID_itr.hasNext()) {
            Instruction i = ID_itr.next();
            
            // Check if scheduling queue is full
            if (issue_list.size() >= issueSize)
                continue;

            // Check if src1 and src2 are in use
            if ((i.src1 != -1 && register[i.src1] != -1) || (i.src2 != -1 && register[i.src2] != -1)) {
                continue;
            }

            // Remove the instruction from the dispatch_list
            ID_itr.remove();
            dispatch_list.remove(i);

            // Transition from the ID state to the IS state
            i.state = State.IS;
            i.d_ID = PC - i.c_ID;
            i.c_IS = PC;
            System.out.println("  Moved " + i.tag + " to IS");
            
            // Add it to the issue_list. Reserve a schedule queue entry 
            issue_list.add(i);            
        }

        Iterator<Instruction> dispatch_itr = dispatch_list.iterator();
        while (dispatch_itr.hasNext()) {
            Instruction i = dispatch_itr.next();

            if (i.state != State.IF)
                continue;

            i.state = State.ID;
            i.d_IF = PC - i.c_IF;
            i.c_ID = PC;
            System.out.println("  Moved " + i.tag + " to ID");
        }
    }

    private static void issue() {
        System.out.println("Issue");
        // From the issue_list, construct a temp list of instructions which are ready
        Collections.sort(issue_list, (i1, i2) -> i1.tag - i2.tag);
        List<Instruction> ready_list = new ArrayList<Instruction>();
        for(Instruction i : issue_list) {
            if (i.dest_ready) {
                if (i.dest != -1)
                    register[i.dest] = i.tag;

                ready_list.add(i);
            }
        }

        // Scan the temp list in ascending order of tags
        Collections.sort(ready_list, (i1, i2) -> i1.tag - i2.tag);
        Iterator<Instruction> ready_itr = ready_list.iterator();

        // Issue up to N+1 of them
        int issueCount = 0;

        while (ready_itr.hasNext() && issueCount < fetchRate) {
            // Remove the instruction from the issue_list
            Instruction i = ready_itr.next();
            issue_list.remove(i);

            i.state = State.EX;
            i.d_IS = PC - i.c_IS;
            i.c_EX = PC;
            
            execute_list.add(i);

            issueCount++;
            ready_itr.remove();
            System.out.println("  Moved " + i.tag + " to EX");
        }
    }

    private static void execute() {
        System.out.println("Execute");
        System.out.println("  " + execute_list.size() + " instructions in list");
        Iterator<Instruction> exe_itr = execute_list.iterator();
        while (exe_itr.hasNext()) {
            Instruction i = exe_itr.next();
            
            // Increment timer to simulate execution latency
            if (i.exeTimer < i.latency - 1) {
                i.exeTimer++; 
                System.out.println("  Instruction " + i.tag + " timer: " + i.exeTimer + "/" + i.latency);
            }
               
            // When the timer finishes, move to WB state
            else {
                
                if (i.dest != -1 && register[i.dest] == i.tag)
                    register[i.dest] = -1;
                
                for (int j = 0; j < issue_list.size(); j++)
                {
                    issue_list.get(j).wake();
                }

                i.state = State.WB;
                i.d_EX = PC - i.c_EX;
                i.c_WB = PC;

                System.out.println("  Instruction " + i.tag + " done executing");
                exe_itr.remove();
            }       
        }
    }

    private static void fakeRetire() {
        System.out.println("FakeRetire");
        while (true) {
            if (!FakeROB.isEmpty() && FakeROB.peek().state == State.WB) {
                Instruction i = FakeROB.dequeue();
                i.d_WB = 1;
                System.out.println("  Retired " + i.tag);
            }
            else {
                break;
            }
        }
    }

    private static boolean advanceCycle() {
        PC++;
        //if (PC > 10) return false;
        return !(FakeROB.isEmpty() && PC > 5);
    }

}

enum State {
    IF,
    ID,
    IS,
    EX,
    WB;
}

class Instruction implements Comparable<Instruction> {
    // Values from trace file
    int pc, op, dest, src1, src2, tag;
    boolean src1_ready = true, src2_ready = true, dest_ready = true;

    // Store cycle and duration instruction was in each state for final output
    State state;
    int c_IF = -1, d_IF = -1;
    int c_ID = -1, d_ID = -1;
    int c_IS = -1, d_IS = -1;
    int c_EX = -1, d_EX = -1;
    int c_WB = -1, d_WB = -1;

    // Values needed for simulation
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
            + "src{" + src1 + "," + src2 + "} "
            + "dst{" + dest + "} "
            + "IF{" + c_IF + "," + d_IF + "} "
            + "ID{" + c_ID + "," + d_ID + "} "
            + "IS{" + c_IS + "," + d_IS + "} "
            + "EX{" + c_EX + "," + d_EX + "} "
            + "WB{" + c_WB + "," + d_WB + "}";
    }

    // Implement the compareTo method for sorting
    @Override
    public int compareTo(Instruction other) {
        return Integer.compare(this.tag, other.tag); // Sort by pc, ascending order
    }

    public void wake() {
        this.dest_ready =
            (src1 == -1 || Simulator.register[src1] == -1) 
            && (src2 == -1 || Simulator.register[src2] == -1)
            && (dest == -1 || Simulator.register[dest] == -1);
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

// From https://codingtechroom.com/tutorial/java-implementing-circular-fifo-queue-in-java
class CircularQueue {
    private Instruction[] queue;
    private int front, rear, capacity;
    public int size = 0;

    public CircularQueue(int size) {
        queue = new Instruction[size];
        capacity = size;
        front = rear = 0;
    }

    public boolean isEmpty() {
        return (size == 0);
    }

    public void enqueue(Instruction item) {
        if ((rear + 1) % capacity == front) {
            return;
        }
        queue[rear] = item;
        rear = (rear + 1) % capacity;
        size++;
    }

    public Instruction dequeue() {
        if (front == rear) {
            return null; // Indicating an error
        }
        Instruction item = queue[front];
        front = (front + 1) % capacity;
        size--;
        return item;
    }

    public Instruction peek() {
        if (front == rear) {
            System.out.println("Queue is empty");
            return null; // Indicate an error
        }
        return queue[front];
    }
}