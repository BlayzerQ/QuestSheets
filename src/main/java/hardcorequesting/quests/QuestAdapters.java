package hardcorequesting.quests;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import cpw.mods.fml.relauncher.ReflectionHelper;
import hardcorequesting.SaveHelper;
import hardcorequesting.reputation.Reputation;
import hardcorequesting.reputation.ReputationMarker;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import questsheets.QuestSheets;
import questsheets.parsing.MinecraftAdapters;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuestAdapters
{
    public static Quest QUEST;
    public static QuestTask TASK;
    public static int QUEST_ID;
    private static Map<Quest, List<Integer>> requirementMapping = new HashMap<>();
    private static Map<Quest, List<Integer>> optionMapping = new HashMap<>();


    public enum TaskType
    {
        CONSUME(QuestTaskItemsConsume.class, "Consume task", "A task where the player can hand in items or fluids. One can also use the Quest Delivery System to submit items and fluids."),
        CRAFT(QuestTaskItemsCrafting.class, "Crafting task", "A task where the player has to craft specific items."),
        LOCATION(QuestTaskLocation.class, "Location task", "A task where the player has to reach one or more locations."),
        CONSUME_QDS(QuestTaskItemsConsumeQDS.class, "QDS task", "A task where the player can hand in items or fluids. This is a normal consume task where manual submit has been disabled to teach the player about the QDS"),
        DETECT(QuestTaskItemsDetect.class, "Detection task", "A task where the player needs specific items. These do not have to be handed in, having them in one\'s inventory is enough."),
        KILL(QuestTaskMob.class, "Killing task", "A task where the player has to kill certain monsters."),
        DEATH(QuestTaskDeath.class, "Death task", "A task where the player has to die a certain amount of times."),
        REPUTATION(QuestTaskReputationTarget.class, "Reputation task", "A task where the player has to reach a certain reputation."),
        REPUTATION_KILL(QuestTaskReputationKill.class, "Rep kill task", "A task where the player has to kill other players with certain reputations.");

        private static Method addTaskData;

        static
        {
            try
            {
                addTaskData = Quest.class.getDeclaredMethod("addTaskData", QuestData.class);
                addTaskData.setAccessible(true);
            } catch (NoSuchMethodException ignored)
            {
            }
        }

        private final Class<? extends QuestTask> clazz;
        private final String name;
        public final String description;

        TaskType(Class<? extends QuestTask> clazz, String name, String description) {
            this.clazz = clazz;
            this.name = name;
            this.description = description;
        }

        public QuestTask addTask(Quest quest)
        {
            QuestTask prev = quest.getTasks().size() > 0?quest.getTasks().get(quest.getTasks().size() - 1):null;
            try {
                Constructor ex = clazz.getConstructor(Quest.class, String.class, String.class);
                QuestTask task = (QuestTask)ex.newInstance(quest, name, description);
                if(prev != null) {
                    task.addRequirement(prev);
                }
                quest.getTasks().add(task);
                SaveHelper.add(SaveHelper.EditType.TASK_CREATE);
                return task;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        public void addTaskData(Quest quest)
        {
            try
            {
                addTaskData.invoke(quest, quest.getQuestData(QuestSheets.getPlayer()));
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        
        public static TaskType getType(Class<? extends QuestTask> clazz)
        {
            for (TaskType type : values())
            {
                if (type.clazz == clazz) return type;
            }
            return CONSUME;
        }
    }
    
    private static final TypeAdapter<QuestTaskItems.ItemRequirement> ITEM_REQUIREMENT_ADAPTER = new TypeAdapter<QuestTaskItems.ItemRequirement>()
    {
        private final String ITEM = "item";
        private final String FLUID = "fluid";
        private final String REQUIRED = "required";
        private final String PRECISION = "precision";
        
        @Override
        public void write(JsonWriter out, QuestTaskItems.ItemRequirement value) throws IOException
        {
            ItemStack item = value.item;
            Fluid fluid = value.fluid;
            int required = value.required;
            ItemPrecision precision = value.precision;
            out.beginObject();
            if (item != null)
            {
                MinecraftAdapters.ITEM_STACK.write(out.name(ITEM), item);
            } else if (fluid != null)
            {
                MinecraftAdapters.FLUID.write(out.name(FLUID), fluid);
            } else
            {
                out.nullValue();
                out.endObject();
                return;
            }
            if (required != 1)
                out.name(REQUIRED).value(required);
            if (precision != ItemPrecision.PRECISE)
            out.name(PRECISION).value(precision.name());
            out.endObject();
        }

        @Override
        public QuestTaskItems.ItemRequirement read(JsonReader in) throws IOException
        {
            in.beginObject();
            ItemStack item = null;
            Fluid fluid = null;
            int required = 1;
            ItemPrecision precision = ItemPrecision.PRECISE;
            while (in.hasNext())
            {
                String next = in.nextName();
                if (next.equalsIgnoreCase(ITEM))
                {
                    item = MinecraftAdapters.ITEM_STACK.read(in);
                } else if (next.equalsIgnoreCase(FLUID))
                {
                    fluid = MinecraftAdapters.FLUID.read(in);
                } else if (next.equalsIgnoreCase(REQUIRED))
                {
                    required = in.nextInt();
                } else if (next.equalsIgnoreCase(PRECISION))
                {
                    ItemPrecision itemPrecision = ItemPrecision.valueOf(in.nextString());
                    if (itemPrecision != null)
                    {
                        precision = itemPrecision;
                    }
                }
            }
            in.endObject();
            QuestTaskItems.ItemRequirement result = null;
            if (item != null)
            {
                result = new QuestTaskItems.ItemRequirement(item, required);
            } else if (fluid != null)
            {
                result = new QuestTaskItems.ItemRequirement(fluid, required);
            } else
            {
                return null;
            }
            result.precision = precision;
            return result;
        }
    };

    private static final TypeAdapter<QuestTaskLocation.Location> LOCATION_ADAPTER = new TypeAdapter<QuestTaskLocation.Location>()
    {
        private final String X = "x";
        private final String Y = "y";
        private final String Z = "z";
        private final String DIM = "dim";
        private final String ICON = "icon";
        private final String RADIUS = "radius";
        private final String VISIBLE = "visible";
        private final String NAME = "name";

        @Override
        public void write(JsonWriter out, QuestTaskLocation.Location value) throws IOException
        {
            out.beginObject();
            out.name(NAME).value(value.getName());
            ItemStack stack = ReflectionHelper.getPrivateValue(QuestTaskLocation.Location.class, value, ICON);
            if (stack != null)
            {
                MinecraftAdapters.ITEM_STACK.write(out.name(ICON), stack);
            }
            out.name(X).value(value.getX());
            out.name(Y).value(value.getY());
            out.name(Z).value(value.getZ());
            out.name(DIM).value(value.getDimension());
            out.name(RADIUS).value(value.getRadius());
            if (value.getVisible() != QuestTaskLocation.Visibility.LOCATION)
                out.name(VISIBLE).value(value.getVisible().name());
            out.endObject();
        }

        @Override
        public QuestTaskLocation.Location read(JsonReader in) throws IOException
        {
            in.beginObject();
            QuestTaskLocation.Location result = ((QuestTaskLocation) TASK).new Location();
            while (in.hasNext())
            {
                String name = in.nextName();
                if (name.equalsIgnoreCase(NAME))
                {
                    ReflectionHelper.setPrivateValue(QuestTaskLocation.Location.class, result, in.nextString(), NAME);
                } else if (name.equalsIgnoreCase(X))
                {
                    result.setX(in.nextInt());
                } else if (name.equalsIgnoreCase(Y))
                {
                    result.setY(in.nextInt());
                } else if (name.equalsIgnoreCase(Z))
                {
                    result.setZ(in.nextInt());
                } else if (name.equalsIgnoreCase(DIM))
                {
                    result.setDimension(in.nextInt());
                } else if (name.equalsIgnoreCase(RADIUS))
                {
                    result.setRadius(in.nextInt());
                } else if (name.equalsIgnoreCase(ICON))
                {
                    ReflectionHelper.setPrivateValue(QuestTaskLocation.Location.class, result, MinecraftAdapters.ITEM_STACK.read(in), ICON);
                } else if (name.equalsIgnoreCase(VISIBLE))
                {
                    result.setVisible(QuestTaskLocation.Visibility.valueOf(in.nextString()));
                }
            }
            in.endObject();
            return result;
        }
    };

    private static final TypeAdapter<QuestTaskMob.Mob> MOB_ADAPTER = new TypeAdapter<QuestTaskMob.Mob>()
    {
        private final String KILLS = "kills";
        private final String EXACT = "exact";
        private final String MOB = "mob";
        private final String ICON = "icon";
        private final String NAME = "name";

        @Override
        public void write(JsonWriter out, QuestTaskMob.Mob value) throws IOException
        {
            out.beginObject();
            out.name(NAME).value(value.getName());
            ItemStack stack = value.getIcon();
            if (stack != null)
            {
                MinecraftAdapters.ITEM_STACK.write(out.name(ICON), stack);
            }
            out.name(MOB).value(value.getMob());
            out.name(KILLS).value(value.getCount());
            out.name(EXACT).value(value.isExact());
            out.endObject();
        }

        @Override
        public QuestTaskMob.Mob read(JsonReader in) throws IOException
        {
            in.beginObject();
            QuestTaskMob.Mob result = ((QuestTaskMob) TASK).new Mob();
            while (in.hasNext())
            {
                String name = in.nextName();
                if (name.equalsIgnoreCase(NAME))
                {
                    result.setName(in.nextString());
                } else if (name.equalsIgnoreCase(ICON))
                {
                    result.setIcon(MinecraftAdapters.ITEM_STACK.read(in));
                } else if (name.equalsIgnoreCase(MOB))
                {
                    result.setMob(in.nextString());
                } else if (name.equalsIgnoreCase(EXACT))
                {
                    result.setExact(in.nextBoolean());
                } else if (name.equalsIgnoreCase(KILLS))
                {
                    result.setCount(in.nextInt());
                }
            }
            in.endObject();
            return result;
        }
    };

    private static final TypeAdapter<QuestTaskReputation.ReputationSetting> REPUTATION_ADAPTER = new TypeAdapter<QuestTaskReputation.ReputationSetting>()
    {
        private final String REPUTATION = "reputation";
        private final String LOWER = "lower";
        private final String UPPER = "upper";
        private final String INVERTED = "inverted";

        @Override
        public void write(JsonWriter out, QuestTaskReputation.ReputationSetting value) throws IOException
        {
            out.beginObject();
            out.name(REPUTATION).value(value.getReputation().getId());
            if (value.getLower() != null)
            {
                out.name(LOWER).value(value.getLower().getId());
            }
            if (value.getUpper() != null)
            {
                out.name(UPPER).value(value.getUpper().getId());
            }
            out.name(INVERTED).value(value.isInverted());
            out.endObject();
        }

        @Override
        public QuestTaskReputation.ReputationSetting read(JsonReader in) throws IOException
        {
            in.beginObject();
            Reputation reputation = null;
            int low = Integer.MIN_VALUE, high = Integer.MIN_VALUE;
            boolean inverted = false;
            while (in.hasNext())
            {
                String name = in.nextName();
                if (name.equalsIgnoreCase(REPUTATION))
                {
                    reputation = Reputation.getReputation(in.nextInt());
                } else if (name.equalsIgnoreCase(UPPER))
                {
                    high = in.nextInt();
                } else if (name.equalsIgnoreCase(LOWER))
                {
                    low = in.nextInt();
                } else if (name.equalsIgnoreCase(INVERTED))
                {
                    inverted = in.nextBoolean();
                }
            }
            if (reputation == null)
            {
                return null;
            }
            ReputationMarker lower = null, upper = null;
            if (low != Integer.MIN_VALUE) lower = reputation.getMarker(low);
            if (high != Integer.MIN_VALUE) upper = reputation.getMarker(high);
            in.endObject();
            return new QuestTaskReputation.ReputationSetting(reputation, lower, upper, inverted);
        }
    };
    
    private static final TypeAdapter<QuestTask> TASK_ADAPTER = new TypeAdapter<QuestTask>()
    {
        private final String TYPE = "type";
        private final String DESCRIPTION = "description";
        private final String LONG_DESCRIPTION = "longDescription";
        private final String ITEMS = "items";
        private final String DEATHS = "deaths";
        private final String LOCATIONS = "locations";
        private final String MOBS = "mobs";
        private final String REPUTATION = "reputation";
        private final String KILLS = "kills";

        @Override
        public void write(JsonWriter out, QuestTask value) throws IOException
        {
            out.beginObject();
            TaskType type = TaskType.getType(value.getClass());
            out.name(TYPE).value(type.name());
            if (!value.getDescription().equals(type.name))
                out.name(DESCRIPTION).value(value.getDescription());
            if (!value.getLongDescription().equals(type.description))
                out.name(LONG_DESCRIPTION).value(value.getLongDescription());
            if (value instanceof QuestTaskItems)
            {
                out.name(ITEMS).beginArray();
                for (QuestTaskItems.ItemRequirement requirement : ((QuestTaskItems) value).getItems())
                {
                    ITEM_REQUIREMENT_ADAPTER.write(out, requirement);
                }
                out.endArray();
            } else if (value instanceof QuestTaskDeath)
            {
                out.name(DEATHS).value(((QuestTaskDeath) value).getDeaths());
            } else if (value instanceof QuestTaskLocation)
            {
                out.name(LOCATIONS).beginArray();
                for (QuestTaskLocation.Location requirement : ((QuestTaskLocation) value).locations)
                {
                    LOCATION_ADAPTER.write(out, requirement);
                }
                out.endArray();
            } else if (value instanceof QuestTaskMob)
            {
                out.name(MOBS).beginArray();
                for (QuestTaskMob.Mob requirement : ((QuestTaskMob) value).mobs)
                {
                    MOB_ADAPTER.write(out, requirement);
                }
                out.endArray();
            } else if (value instanceof QuestTaskReputation)
            {
                out.name(REPUTATION).beginArray();
                for (QuestTaskReputation.ReputationSetting requirement : ((QuestTaskReputation) value).getSettings())
                {
                    REPUTATION_ADAPTER.write(out, requirement);
                }
                out.endArray();
                if (value instanceof QuestTaskReputationKill)
                {
                    out.name(KILLS).value(((QuestTaskReputationKill) value).getKills());
                }
            }
            out.endObject();
        }

        @Override
        public QuestTask read(JsonReader in) throws IOException
        {
            in.beginObject();
            if (!in.nextName().equalsIgnoreCase(TYPE))
            {
                throw new IOException("Tasks *MUST* start with the type");
            }
            String task = in.nextString();
            TaskType type = TaskType.valueOf(task);
            if (type == null)
            {
                throw new IOException("Invalid Task Type: " + task);
            }
            TASK = type.addTask(QUEST);
            while (in.hasNext())
            {
                String name = in.nextName();
                if (name.equalsIgnoreCase(DESCRIPTION))
                {
                    TASK.description = in.nextString();
                } else if (name.equalsIgnoreCase(LONG_DESCRIPTION))
                {
                    TASK.setLongDescription(in.nextString());
                } else if (TASK instanceof QuestTaskItems && name.equalsIgnoreCase(ITEMS))
                {
                    List<QuestTaskItems.ItemRequirement> list = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext())
                    {
                        QuestTaskItems.ItemRequirement entry = ITEM_REQUIREMENT_ADAPTER.read(in);
                        if (entry != null) list.add(entry);
                    }
                    in.endArray();
                    ((QuestTaskItems) TASK).setItems(list.toArray(new QuestTaskItems.ItemRequirement[list.size()]));
                } else if (TASK instanceof QuestTaskDeath && name.equalsIgnoreCase(DEATHS))
                {
                    int death = in.nextInt();
                    ((QuestTaskDeath) TASK).setDeaths(death);
                } else if (TASK instanceof QuestTaskLocation &&name.equalsIgnoreCase(LOCATIONS))
                {
                    List<QuestTaskLocation.Location> list = new ArrayList<QuestTaskLocation.Location>();
                    in.beginArray();
                    while (in.hasNext())
                    {
                        QuestTaskLocation.Location entry = LOCATION_ADAPTER.read(in);
                        if (entry != null) list.add(entry);
                    }
                    in.endArray();
                    ((QuestTaskLocation) TASK).locations = list.toArray(new QuestTaskLocation.Location[list.size()]);
                } else if (TASK instanceof QuestTaskMob && name.equalsIgnoreCase(MOBS))
                {
                    List<QuestTaskMob.Mob> list = new ArrayList<QuestTaskMob.Mob>();
                    in.beginArray();
                    while (in.hasNext())
                    {
                        QuestTaskMob.Mob entry = MOB_ADAPTER.read(in);
                        if (entry != null) list.add(entry);
                    }
                    in.endArray();
                    ((QuestTaskMob) TASK).mobs = list.toArray(new QuestTaskMob.Mob[list.size()]);
                } else if (TASK instanceof QuestTaskReputation && name.equalsIgnoreCase(REPUTATION))
                {
                    List<QuestTaskReputation.ReputationSetting> list = new ArrayList<QuestTaskReputation.ReputationSetting>();
                    in.beginArray();
                    while (in.hasNext())
                    {
                        list.add(REPUTATION_ADAPTER.read(in));
                    }
                    in.endArray();
                    ReflectionHelper.setPrivateValue(QuestTaskReputation.class, (QuestTaskReputation) TASK, list.toArray(new QuestTaskReputation.ReputationSetting[list.size()]), "settings");
                }
                else if (name.equalsIgnoreCase(KILLS) && TASK instanceof QuestTaskReputationKill)
                {
                    ((QuestTaskReputationKill) TASK).setKills(in.nextInt());
                }
            }
            in.endObject();
            type.addTaskData(QUEST);
            return null;
        }
    };

    private static final TypeAdapter<RepeatInfo> REPEAT_INFO_ADAPTER = new TypeAdapter<RepeatInfo>()
    {
        private final String TYPE = "type";
        private final String HOURS = "hours";
        private final String DAYS = "days";

        @Override
        public void write(JsonWriter out, RepeatInfo value) throws IOException
        {
            out.beginObject();
            out.name(TYPE).value(value.getType().name());
            if (value.getType().isUseTime())
            {
                out.name(DAYS).value(value.getDays());
                out.name(HOURS).value(value.getHours());
            }
            out.endObject();
        }

        @Override
        public RepeatInfo read(JsonReader in) throws IOException
        {
            RepeatType type = RepeatType.NONE;
            int days = 0, hours = 0;
            in.beginObject();
            while (in.hasNext())
            {
                switch(in.nextName())
                {
                    case TYPE:
                        type = RepeatType.valueOf(in.nextString());
                        break;
                    case HOURS:
                        hours = in.nextInt();
                        break;
                    case DAYS:
                        days = in.nextInt();
                }
            }
            in.endObject();
            return new RepeatInfo(type, days, hours);
        }
    };

    private static final TypeAdapter<Quest.ReputationReward> REPUTATION_REWARD_ADAPTER = new TypeAdapter<Quest.ReputationReward>()
    {
        private final String REPUTATION = "reputation";
        private final String VALUE = "value";

        @Override
        public void write(JsonWriter out, Quest.ReputationReward value) throws IOException
        {
            out.beginObject();
            out.name(REPUTATION).value(value.getReputation().getId());
            out.name(VALUE).value(value.getValue());
            out.endObject();
        }

        @Override
        public Quest.ReputationReward read(JsonReader in) throws IOException
        {
            in.beginObject();
            int rep = 0, val = 0;
            while (in.hasNext())
            {
                switch (in.nextName())
                {
                    case REPUTATION:
                        rep = in.nextInt();
                        break;
                    case VALUE:
                        val = in.nextInt();
                        break;
                }
            }
            in.endObject();
            Reputation reputation = Reputation.getReputation(rep);
            return reputation != null ? new Quest.ReputationReward(reputation, val) : null;
        }
    };

    public static final TypeAdapter<Quest> QUEST_ADAPTER = new TypeAdapter<Quest>()
    {
        private final String NAME = "name";
        private final String DESCRIPTION = "description";
        private final String X = "x";
        private final String Y = "y";
        private final String ICON = "icon";
        private final String BIG_ICON = "bigIcon";
        private final String REQUIREMENTS = "requirements";
        private final String OPTIONS = "options";
        private final String REPEAT = "repeat";
        private final String TRIGGER = "trigger";
        private final String TRIGGER_TASKS = "triggerTasks";
        private final String PARENT_REQUIREMENT = "parentRequirement";
        private final String TASKS = "tasks";
        private final String REWARDS = "reward";
        private final String REWARDS_CHOICE = "rewardChoice";
        private final String REWARDS_REPUTATION = "reputationRewards";

        @Override
        public void write(JsonWriter out, Quest value) throws IOException
        {
            out.beginObject();
            out.name(NAME).value(value.getName());
            out.name(DESCRIPTION).value(value.getDescription());
            out.name(X).value(value.getGuiX());
            out.name(Y).value(value.getGuiY());
            if (value.useBigIcon())
            {
                out.name(BIG_ICON).value(true);
            }
            if (value.getIcon() != null)
            {
                MinecraftAdapters.ITEM_STACK.write(out.name(ICON), value.getIcon());
            }
            writeQuestList(out, value.getRequirement(), value.getQuestSet().getQuests(), REQUIREMENTS);
            writeQuestList(out, value.getOptionLinks(), value.getQuestSet().getQuests(), OPTIONS);
            if (value.getRepeatInfo().getType() != RepeatType.NONE)
            {
                REPEAT_INFO_ADAPTER.write(out.name(REPEAT), value.getRepeatInfo());
            }
            if (value.getTriggerType() != TriggerType.NONE)
            {
                out.name(TRIGGER).value(value.getTriggerType().name());
            }
            if (value.getTriggerType().isUseTaskCount())
            {
                out.name(TRIGGER_TASKS).value(value.getTriggerTasks());
            }
            if (value.getUseModifiedParentRequirement())
            {
                out.name(PARENT_REQUIREMENT).value(value.getParentRequirementCount());
            }
            out.name(TASKS).beginArray();
            for (QuestTask task : value.getTasks())
            {
                TASK_ADAPTER.write(out, task);
            }
            out.endArray();
            writeItemStackArray(out, (ItemStack[]) ReflectionHelper.getPrivateValue(Quest.class, value, REWARDS), REWARDS);
            writeItemStackArray(out, (ItemStack[]) ReflectionHelper.getPrivateValue(Quest.class, value, REWARDS_CHOICE), REWARDS_CHOICE);
            if (value.getReputationRewards() != null && !value.getReputationRewards().isEmpty())
            {
                out.name(REWARDS_REPUTATION).beginArray();
                for (Quest.ReputationReward reward : value.getReputationRewards())
                {
                    REPUTATION_REWARD_ADAPTER.write(out, reward);
                }
                out.endArray();
            }
            out.endObject();
        }

        private void writeQuestList(JsonWriter out, List<Quest> quests, List<Quest> setQuests, String name) throws IOException
        {
            List<Quest> inSetQuests = new ArrayList<>(setQuests);
            inSetQuests.retainAll(quests);
            if (!inSetQuests.isEmpty())
            {
                out.name(name).beginArray();
                for (Quest quest : inSetQuests)
                {
                    int index = setQuests.indexOf(quest);
                    if (index != -1)
                    {
                        out.value(index);
                    }
                }
                out.endArray();
            }
        }

        private void writeItemStackArray(JsonWriter out, ItemStack[] stacks, String name) throws IOException
        {
            if (stacks != null)
            {
                out.name(name).beginArray();
                for (ItemStack stack : stacks)
                {
                    if (stack != null)
                    {
                        MinecraftAdapters.ITEM_STACK.write(out, stack);
                    }
                }
                out.endArray();
            }
        }

        @Override
        public Quest read(JsonReader in) throws IOException
        {
            int ID_OFFSET = Quest.size();
            QUEST = new Quest(ID_OFFSET, "", "", 0, 0, false);
            List<Integer> requirement = new ArrayList<>(), options = new ArrayList<>();
            in.beginObject();
            while (in.hasNext())
            {
                switch (in.nextName())
                {
                    case NAME:
                        QUEST.setName(in.nextString());
                        break;
                    case DESCRIPTION:
                        QUEST.setDescription(in.nextString());
                        break;
                    case X:
                        ReflectionHelper.setPrivateValue(Quest.class, QUEST, in.nextInt(), X);
                        break;
                    case Y:
                        ReflectionHelper.setPrivateValue(Quest.class, QUEST, in.nextInt(), Y);
                        break;
                    case TRIGGER_TASKS:
                        QUEST.setTriggerTasks(in.nextInt());
                        break;
                    case PARENT_REQUIREMENT:
                        QUEST.setParentRequirementCount(in.nextInt());
                        break;
                    case BIG_ICON:
                        QUEST.setBigIcon(in.nextBoolean());
                        break;
                    case ICON:
                        QUEST.setIcon(MinecraftAdapters.ITEM_STACK.read(in));
                        break;
                    case REQUIREMENTS:
                        in.beginArray();
                        while (in.hasNext())
                        {
                            requirement.add(in.nextInt() + QUEST_ID);
                        }
                        in.endArray();
                        break;
                    case OPTIONS:
                        in.beginArray();
                        while (in.hasNext())
                        {
                            options.add(in.nextInt() + QUEST_ID);
                        }
                        in.endArray();
                        break;
                    case REPEAT:
                        QUEST.setRepeatInfo(REPEAT_INFO_ADAPTER.read(in));
                        break;
                    case TRIGGER:
                        QUEST.setTriggerType(TriggerType.valueOf(in.nextString()));
                        break;
                    case TASKS:
                        in.beginArray();
                        while (in.hasNext())
                        {
                            QuestTask task = TASK_ADAPTER.read(in);
                            if (task != null)
                            {
                                QUEST.getTasks().add(task);

                            }
                        }
                        in.endArray();
                        break;
                    case REWARDS:
                        ReflectionHelper.setPrivateValue(Quest.class, QUEST, readItemStackArray(in), REWARDS);
                        break;
                    case REWARDS_CHOICE:
                        ReflectionHelper.setPrivateValue(Quest.class, QUEST, readItemStackArray(in), REWARDS_CHOICE);
                        break;
                    case REWARDS_REPUTATION:
                        in.beginArray();
                        List<Quest.ReputationReward> reputationRewards = new ArrayList<>();
                        while (in.hasNext())
                        {
                            Quest.ReputationReward reward = REPUTATION_REWARD_ADAPTER.read(in);
                            if (reward != null)
                                reputationRewards.add(reward);
                        }
                        QUEST.setReputationRewards(reputationRewards);
                        in.endArray();
                        break;
                }
            }
            in.endObject();
            if (!QUEST.getName().isEmpty())
            {
                requirementMapping.put(QUEST, requirement);
                optionMapping.put(QUEST, options);
                return QUEST;
            }
            QuestLine.getActiveQuestLine().quests.remove(QUEST.getId());
            return null;
        }

        private ItemStack[] readItemStackArray(JsonReader in) throws IOException
        {
            List<ItemStack> stacks = new ArrayList<>();
            in.beginArray();
            while (in.hasNext())
            {
                ItemStack stack = MinecraftAdapters.ITEM_STACK.read(in);
                if (stack != null)
                    stacks.add(stack);
            }
            in.endArray();
            return stacks.toArray(new ItemStack[stacks.size()]);
        }
    };

    public static final TypeAdapter<QuestSet> QUEST_SET_ADAPTER = new TypeAdapter<QuestSet>()
    {
        private final String NAME = "name";
        private final String DESCRIPTION = "description";
        private final String QUESTS = "quests";

        @Override
        public void write(JsonWriter out, QuestSet value) throws IOException
        {
            out.beginObject();
            out.name(NAME).value(value.getName());
            out.name(DESCRIPTION).value(value.getDescription());
            out.name(QUESTS).beginArray();
            for (Quest quest : value.getQuests())
            {
                QUEST_ADAPTER.write(out, quest);
            }
            out.endArray().endObject();
        }

        @Override
        public QuestSet read(JsonReader in) throws IOException
        {
            String name = null, description = null;
            requirementMapping.clear();
            optionMapping.clear();
            List<Quest> quests = new ArrayList<Quest>();
            in.beginObject();
            while (in.hasNext())
            {
                String next = in.nextName();
                if (next.equalsIgnoreCase(NAME))
                {
                    name = in.nextString();
                } else if (next.equalsIgnoreCase(DESCRIPTION))
                {
                    description = in.nextString();
                } else if (next.equalsIgnoreCase(QUESTS))
                {
                    in.beginArray();
                    QUEST_ID = Quest.size();
                    while (in.hasNext())
                    {
                        Quest quest = QUEST_ADAPTER.read(in);
                        if (quest != null)
                        {
                            quests.add(quest);
                        }
                    }
                    in.endArray();
                }
            }
            in.endObject();
            for (QuestSet set : Quest.getQuestSets())
            {
                if (set.getName().equals(name))
                {
                    return removeQuests(quests);
                }
            }
            if (name != null && description != null)
            {
                QuestSet set = new QuestSet(name, description);
                Quest.getQuestSets().add(set);
                SaveHelper.add(SaveHelper.EditType.SET_CREATE);
                for (Quest quest : quests)
                {
                    quest.setQuestSet(set);
                }
                for (Map.Entry<Quest, List<Integer>> entry : requirementMapping.entrySet())
                {
                    for (int i : entry.getValue())
                        entry.getKey().addRequirement(i);
                }
                for (Map.Entry<Quest, List<Integer>> entry : optionMapping.entrySet())
                {
                    for (int i : entry.getValue())
                        entry.getKey().addOptionLink(i);
                }
                return set;
            }
            return removeQuests(quests);
        }

        private QuestSet removeQuests(List<Quest> quests)
        {
            for (Quest quest : quests)
            {
                QuestLine.getActiveQuestLine().quests.remove(quest.getId());
            }
            return null;
        }
    };
}