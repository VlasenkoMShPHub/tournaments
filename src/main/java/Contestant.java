import javafx.util.Pair;

public class Contestant {
    public String name;
    public int score = 0;
    public int scoreNow = 0;
    public int wins = 0;
    public Pair<Integer, Integer> position;

    public Contestant(String Name, int Score, int ScoreNow, int Wins, Pair<Integer, Integer> pos){
        name = Name;
        score = Score;
        scoreNow = ScoreNow;
        wins = Wins;
        position = pos;
    }
    // here is the overloading
    public Contestant(String Name){
        name = Name;
    }
}
