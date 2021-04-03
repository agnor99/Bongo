package io.github.noeppi_noeppi.mods.bongo.data;

import com.mojang.datafixers.util.Either;
import io.github.noeppi_noeppi.mods.bongo.task.Task;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NumberNBT;
import net.minecraftforge.common.util.Constants;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GameTaskGroup {
    
    public static final String PSEUDO_TYPE = "bongo.group";
    
    private final List<Pair<Integer, Either<Task, GameTaskGroup>>> tasks;
    private final int totalWeight;
    
    private GameTaskGroup(ListNBT taskList) {
        tasks = new ArrayList<>();
        int totalWeight = 0;
        for (int i = 0; i < taskList.size(); i++) {
            CompoundNBT compound = taskList.getCompound(i);
            boolean allowsForSpecialWeights = PSEUDO_TYPE.equals(compound.getString("type"));
            int[] weights;
            if (!compound.contains("weight")) {
                weights = new int[]{ 1 };
            } else if (allowsForSpecialWeights && compound.contains("weight", Constants.NBT.TAG_LIST)) {
                ListNBT weightsNBT = compound.getList("weight", Constants.NBT.TAG_INT);
                weights = new int[weightsNBT.size()];
                for (int j = 0; j < weightsNBT.size(); j++) {
                    weights[j] = weightsNBT.getInt(j);
                }
            } else if (allowsForSpecialWeights && compound.contains("weight", Constants.NBT.TAG_INT_ARRAY)) {
                weights = compound.getIntArray("weight");
            } else if (allowsForSpecialWeights && compound.contains("weight", Constants.NBT.TAG_LONG_ARRAY)) {
                weights = Arrays.stream(compound.getLongArray("weight")).mapToInt(l -> (int) l).toArray();
            } else if (allowsForSpecialWeights && compound.contains("weight", Constants.NBT.TAG_BYTE_ARRAY)) {
                byte[] weightsNBT = compound.getByteArray("weight");
                weights = new int[weightsNBT.length];
                for (int j = 0; j < weightsNBT.length; j++) {
                    weights[j] = weightsNBT[j];
                }
            } else if (compound.get("weight") instanceof NumberNBT) {
                weights = new int[]{ compound.getInt("weight") };
            } else {
                throw new IllegalStateException("Invalid weight for task. weight must be an integer. Only for task groups it may be an array of integers.");
            }
            
            Either<Task, GameTaskGroup> task = parseTask(compound);
            
            for (int weight : weights) {
                int realWeight = Math.max(weight, 1);
                tasks.add(Pair.of(realWeight, task));
                totalWeight += realWeight;
            }
            
        }
        this.totalWeight = totalWeight;
    }
    
    public int getAvailableTasks() {
        return tasks.size();
    }
    
    public Either<List<Task>, String> choseTasks(Random random, int taskAmount) {
        if (tasks.size() < taskAmount) {
            return Either.right("bongo.cmd.create.less.subgroup");
        }

        int weightLeft = totalWeight;
        int collectedTasks = 0;
        List<Task> theTasks = new ArrayList<>();
        Map<GameTaskGroup, Integer> theGroups = new HashMap<>();
        List<Integer> theTasksIndices = new ArrayList<>();
        while (collectedTasks < taskAmount) {
            int rand = random.nextInt(weightLeft);
            int weightCounted = 0;
            for (int i = 0; i < tasks.size(); i++) {
                if (!theTasksIndices.contains(i)) {
                    Pair<Integer, Either<Task, GameTaskGroup>> pair = tasks.get(i);
                    weightCounted += pair.getLeft();
                    if (weightCounted > rand) {
                        Either<Task, GameTaskGroup> taskDef = pair.getRight();
                        if (taskDef.left().isPresent()) {
                            theTasks.add(taskDef.left().get());
                        } else if (taskDef.right().isPresent()) {
                            GameTaskGroup group = taskDef.right().get();
                            if (!theGroups.containsKey(group)) {
                                theGroups.put(group, 0);
                            }
                            theGroups.put(group, theGroups.get(group) + 1);
                        }
                        theTasksIndices.add(i);
                        collectedTasks += 1;
                        weightLeft -= pair.getLeft();
                        break;
                    }
                }
            }
        }
        for (Map.Entry<GameTaskGroup, Integer> entry : theGroups.entrySet()) {
            if (entry.getValue() > 0) {
                Either<List<Task>, String> result = entry.getKey().choseTasks(random, entry.getValue());
                if (result.right().isPresent() || !result.left().isPresent()) {
                    return Either.right(result.right().isPresent() ? result.right().get() : "Unknown Error");
                } else {
                    theTasks.addAll(result.left().get());
                }
            }
        }
        // If this was not here rare items would be more likely to appear in the first row.
        Collections.shuffle(theTasks);
        return Either.left(theTasks);
    }
    
    public List<Task> getTasksSorted() {
        return this.tasks.stream()
                .map(Pair::getRight)
                .flatMap(e -> e.left().map(Stream::of).orElse(Stream.empty()))
                .collect(Collectors.toList());
    }
    
    public static Either<Task, GameTaskGroup> parseTask(CompoundNBT nbt) {
        if (PSEUDO_TYPE.equals(nbt.getString("type"))) {
            ListNBT tasks = nbt.getList("tasks", Constants.NBT.TAG_COMPOUND);
            if (tasks.size() == 1) {
                return parseTask(tasks.getCompound(0));
            } else {
                return Either.right(new GameTaskGroup(tasks));
            }
        } else {
            Task task = Task.empty();
            task.deserializeNBT(nbt);
            return Either.left(task);
        }
    }
    
    public static GameTaskGroup parseRootTasks(CompoundNBT nbt) {
        ListNBT tasks = nbt.getList("tasks", Constants.NBT.TAG_COMPOUND);
        return new GameTaskGroup(tasks);
    }
}
