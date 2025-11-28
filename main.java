import java.util.*;
import jade.core.Runtime;
import jade.core.behaviours.TickerBehaviour;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.Profile;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentContainer;
import jade.core.ProfileImpl;
import java.io.Serializable;
import jade.lang.acl.MessageTemplate;

////КОНФИГУРАЦИЯ МУЛЬТИАГЕНТНОЙ СЕТИ
public class main extends Agent {
    static class AgentState implements Serializable {
        int id; 
        double currentValue;
        AgentState(int i, double v) { id = i; currentValue = v; }}
    static class Config {
        static final int agents_num = 10;
        static final int iterations = 100;
        static final double alpha = 0.2;
        static List<int[]> allEdges = new ArrayList<>();
        static List<int[]> removedEdges = new ArrayList<>();
        static HashMap<Integer, List<Integer>> adjacencyList = new HashMap<>();
        static HashMap<Integer, Double> initialValues = new HashMap<>();
        static {
            int[][] connection = {{1, 2, 3, 10}, {2, 1, 4, 5}, {3, 1, 6, 7}, {4, 2, 8, 9}, {5, 2, 6, 10},
                   {6, 3, 5, 8}, {7, 3, 9, 10}, {8, 4, 6, 7}, {9, 4, 7, 1}, {10, 5, 7, 9}};
            double[] data = {730, 653, 918, 555, 839, 741, 754, 748, 942, 667};
            for (int i = 0; i < connection.length; i++) {
                List<Integer> neighbor = new ArrayList<>();
                for (int j = 1; j < connection[i].length; j++) {
                    neighbor.add(connection[i][j]);}
                adjacencyList.put(connection[i][0], neighbor);
                initialValues.put(i + 1, data[i]);}
            for (int i = 1; i <= agents_num; i++) {
                for (int j : adjacencyList.get(i)) {
                    if (i < j) allEdges.add(new int[]{i, j});}}}
        static List<Integer> getAdjacents(int id) { return adjacencyList.get(id); }
        static double getInitialValue(int id) { return initialValues.get(id); }}
    AgentState data;
    int currentIteration = 0;
    boolean shouldStop = false;
    List<Double> incoming = new ArrayList<>();
    @Override
    protected void setup() {
        int id = Integer.parseInt(getLocalName().replace("Node", ""));
        data = new AgentState(id, Config.getInitialValue(id));
        addBehaviour(new MessageReceiver());
        addBehaviour(new MessageSender(this, 3000));
        addBehaviour(new StopBehaviour());
        if (id == 1) addBehaviour(new DataAggregator());}
    class MessageSender extends TickerBehaviour {
        MessageSender(Agent a, long period) { super(a, period); }
        @Override
        protected void onTick() {
            if (shouldStop) { stop(); return; }
            if (++currentIteration > Config.iterations) {
                shouldStop = true;
                ACLMessage stopMsg = new ACLMessage(ACLMessage.CANCEL);
                for (int i = 1; i <= Config.agents_num; i++)
                    stopMsg.addReceiver(new jade.core.AID("Node" + i, jade.core.AID.ISLOCALNAME));
                send(stopMsg);
                return;}
            if (data.id == 1) Drop();
            List<Integer> activeNeighbors = getActiveNeighbors();
            Chating(activeNeighbors);
            sendStatus();}

// РЕАЛИЗАЦИЯ ОТБРАСЫВАНИЯ РЁБЕР
        private void Drop() {
            Config.removedEdges.clear();
            List<int[]> edges = new ArrayList<>(Config.allEdges);
            Collections.shuffle(edges);
            int wich = (int)(Math.random() * 3);
            wich = Math.min(wich, edges.size());
            for (int i = 0; i < wich; i++)
                Config.removedEdges.add(edges.get(i));}
        private List<Integer> getActiveNeighbors() {
            List<int[]> currentlyRemoved = new ArrayList<>(Config.removedEdges);
            List<Integer> active = new ArrayList<>();
            for (int n : Config.getAdjacents(data.id)) {
                boolean isDropped = false;
                for (int[] edge : currentlyRemoved) {
                    if (edge != null && ((edge[0] == data.id && edge[1] == n) || (edge[0] == n && edge[1] == data.id))) {
                        isDropped = true; break;}}
                if (!isDropped) active.add(n);  }
            return active;}
        private void Chating(List<Integer> neighbors) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            try {
                msg.setContentObject(new AgentState(data.id, data.currentValue));
                for (int neighbor : neighbors)
                    msg.addReceiver(new jade.core.AID("Node" + neighbor, jade.core.AID.ISLOCALNAME));
                send(msg);}
            catch (Exception e) {
                System.err.println("Send error " + data.id + ": " + e.getMessage());}}

        private void sendStatus() {
            ACLMessage statusMsg = new ACLMessage(ACLMessage.REQUEST);
            statusMsg.addReceiver(new jade.core.AID("Node1", jade.core.AID.ISLOCALNAME));
            statusMsg.setContent(currentIteration + "," + data.id + "," + data.currentValue);
            send(statusMsg);}}
    
// ПРОТОКОЛ ЛОКАЛЬНОГО ГОЛОСОВАНИЯ, ШУМ ПРИ ПРИЁМЕ СООБЩЕНИЙ
    class MessageReceiver extends Behaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.INFORM));
            if (msg != null) {
                try {
                    AgentState otherState = (AgentState) msg.getContentObject();
                    double noisycurrentValue = otherState.currentValue + (Math.random() - 0.5);
                    incoming.add(noisycurrentValue);
                    if (incoming.size() == getActiveNeighbors().size()) {
                        double differences = 0.0;
                        for (double v : incoming) {
                            differences += (v - data.currentValue);}
                        data.currentValue += Config.alpha * differences;
                        incoming.clear();}}
                catch (Exception e) {
                    System.err.println("Receive error " + data.id + ": " + e.getMessage());}}
            else {
                block(); }}
        @Override
        public boolean done() { return false; }}
    class StopBehaviour extends Behaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.CANCEL));
            if (msg != null) {
                shouldStop = true;}
            else {
                block();}}
        @Override
        public boolean done() { return shouldStop; }}
    
    class DataAggregator extends Behaviour {
        private HashMap<Integer, Double> collectedValues = new HashMap<>();
        private int currentStep = 1;
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
            if (msg != null) {
                String[] parts = msg.getContent().split(",");
                int msgStep = Integer.parseInt(parts[0]);
                int id = Integer.parseInt(parts[1]);
                double currentValue = Double.parseDouble(parts[2]);
                if (msgStep == currentStep) {
                    collectedValues.put(id, currentValue);
                    if (collectedValues.size() == Config.agents_num) {
                        Print();
                        collectedValues.clear();
                        if (currentStep == Config.iterations) {
                            TrueAverage();}
                        currentStep++;}}}
            else {
                block();}}

        private void Print() {
            System.out.println();
            if ((currentStep-1) % 3 != 0) return;
            System.out.println("Stage " + currentStep);
            for (int i = 1; i <= Config.agents_num; i++)
                System.out.println("Node " + i + ": " + collectedValues.get(i));
            System.out.print("Dropped edge: ");
            if (Config.removedEdges.isEmpty()) System.out.println("-");
            else {
                for (int i = 0; i < Config.removedEdges.size(); i++) {
                    int[] edge = Config.removedEdges.get(i);
                    if (edge != null) {
                        System.out.print("(" + edge[0] + "," + edge[1] + ")");
                        if (i < Config.removedEdges.size() - 1) System.out.print(", ");}}
                System.out.println();}}

        // ВЫЧИСЛЕНИЕ СРЕДНЕГО
        private void TrueAverage() {
            double sum = 0.0;
            for (int i = 1; i <= Config.agents_num; i++) {
                double val = Config.getInitialValue(i);
                sum += val;}
            System.out.println("True average: " + (sum / Config.agents_num));}
        @Override
        public boolean done() { return false; }}

    private List<Integer> getActiveNeighbors() {
        List<int[]> currentlyRemoved = new ArrayList<>(Config.removedEdges);
        List<Integer> active = new ArrayList<>();
        for (int n : Config.getAdjacents(data.id)) {
            boolean isDropped = false;
            for (int[] edge : currentlyRemoved) {
                if (edge != null && ((edge[0] == data.id && edge[1] == n) || (edge[0] == n && edge[1] == data.id))) {
                    isDropped = true; break;}}
            if (!isDropped) active.add(n);}
        return active;}

//ЗАПУСК
    public static void main(String[] args) throws Exception {
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.MAIN_PORT, "1099");
        p.setParameter(Profile.GUI, "true");
        AgentContainer container = rt.createMainContainer(p);
        for (int i = 1; i <= Config.agents_num; i++) {
            container.createNewAgent("Node" + i, main.class.getName(), new Object[]{}).start();}}}
