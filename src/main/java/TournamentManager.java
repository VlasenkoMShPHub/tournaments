/*
MIT License

Copyright (c) 2020 Mikhail Vlasenko, IB DP Student

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;

import com.google.common.collect.Lists;
import javafx.util.Pair;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.*;

public class TournamentManager {
    private static String SHEET_ID = "1aQqQ9ldMDDjqU0rG1en9B5_tnnX_cpJr1ZHmi5k29ac";
    private static Sheets sheetsService;
    private static final String APPLICATION_NAME = "Tournament Manager";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/client_id.json";
    private static final int MAX_CONTESTANTS = 16;
    private static Map<String, Contestant> contestants = new HashMap<>();

    private static Credential authorize() throws IOException, GeneralSecurityException {
        // Load client secrets.
        InputStream in = TournamentManager.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    private static Sheets getSheetsService() throws IOException, GeneralSecurityException {
        Credential credential = authorize();
        return new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private static List<List<Object>> read(String range) throws IOException {
        if (!range.contains("!")){
            System.out.println(range);
            throw new IllegalArgumentException("sheet not specified");
        }
        range = range.toUpperCase();
        ValueRange response = sheetsService.spreadsheets().values().get(SHEET_ID, range).execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()){
            System.out.println("No data found");
        }
        return values;
    }

    private static void write(String range, List<List<Object>> values) throws IOException {
        if (!range.contains("!")){
            System.out.println(range);
            throw new IllegalArgumentException("sheet not specified");
        }
        range = range.toUpperCase();
        ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values().update(SHEET_ID, range, body).setValueInputOption("RAW").execute();
    }

    private static List<Integer> argsort(List<Integer> a) {
        Integer[] indexes = new Integer[a.size()];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, (i1, i2) -> Float.compare(a.get(i1), a.get(i2))); // lambda
        return Arrays.asList(indexes);
    }

    private static Pair<String, String> waitExecute()
            throws InterruptedException, IOException {
        List<List<Object>> values = new ArrayList<>();
        boolean flag = false;
        String[] sheets = {"Main!", "Tournament Structure!", "Total stats!", "Stats!"};
        String action, param, exe_sheet = "";

        while (!flag) {
            try {
                for (String sheet : sheets) {
                    values = read(sheet + "e1");
                    if (!values.get(0).get(0).equals("type action to execute")){
                        flag = true;
                        exe_sheet = sheet;
                        break;
                    }
                }
            }catch (java.net.SocketTimeoutException e){
                System.err.println("Caught TimeoutException");
            }
            Thread.sleep(1000);
        }
        action = (String) values.get(0).get(0);
        param = exe_sheet + values.get(0).get(1);
        write(exe_sheet + "e1:f1", Arrays.asList(Arrays.asList("type action to execute", "execution parameter")));
        return new Pair<>(action, param);
    }

    private static void getNames() throws IOException {
        List<List<Object>> range = read("Main!a1");
        int numRange;
        try {
            numRange = Integer.parseInt((String) range.get(0).get(0));
        }catch (java.lang.NumberFormatException e) {
            System.out.println("input a number in A1");
            return;
        }
        List<List<Object>> values = read("Main!a2:a" + (numRange + 1));
        for (List row : values){
            contestants.put((String) row.get(0), new Contestant((String) row.get(0)));
        }
    }

    private static void addTab(String title, int id) throws IOException {
        boolean flag = false;
        Spreadsheet ssheet = sheetsService.spreadsheets().get(SHEET_ID).execute();
        for (Sheet sheet : ssheet.getSheets()) {
            if (sheet.getProperties().getTitle().equals(title)){
                System.out.printf("sheet %s already exists\n", title);
                flag = true;
            }
        }
        if (!flag) {
            List<Request> requests = new ArrayList<>();
            AddSheetRequest addSheet = new AddSheetRequest().setProperties(
                    new SheetProperties().setTitle(title).setSheetId(id));
            Request request = new Request().setAddSheet(addSheet);
            requests.add(request);
            BatchUpdateSpreadsheetRequest requestBody = new BatchUpdateSpreadsheetRequest();
            requestBody.setRequests(requests);
            sheetsService.spreadsheets().batchUpdate(SHEET_ID, requestBody).execute();
        }
    }

    private static void renameTab() throws IOException, GeneralSecurityException {
        sheetsService = getSheetsService();
        // create a SheetProperty object and put there all your parameters (title, sheet id, something else)
        SheetProperties title = new SheetProperties().setSheetId(0).setTitle("Main");
        // make a request with this properties
        UpdateSheetPropertiesRequest rename = new UpdateSheetPropertiesRequest().setProperties(title);
        // set fields you want to update
        rename.setFields("title");
        // as requestBody.setRequests gets a list, you need to compose an list from your request
        List<Request> requests = new ArrayList<>();
        // convert to Request
        Request request = new Request().setUpdateSheetProperties(rename);
        requests.add(request);
        BatchUpdateSpreadsheetRequest requestBody = new BatchUpdateSpreadsheetRequest();
        requestBody.setRequests(requests);
        // now you can execute batchUpdate with your sheetsService and SHEET_ID
        sheetsService.spreadsheets().batchUpdate(SHEET_ID, requestBody).execute();
    }

    private static List<List<Object>> checkNames() throws IOException, GeneralSecurityException {
        if (contestants.size() == 0){
            getNames();
        }
        List<List<Object>> names = new ArrayList<>();
        for (Contestant con : contestants.values()){
            names.add(Arrays.asList(con.name));
        }
        return names;
    }

    private static List<List<Object>> contestantsToList(Map<String, Contestant> conts){
        List<List<Object>> values = new ArrayList<>();
        for (Contestant c : conts.values()){
            values.add(Arrays.asList(c.name, String.valueOf(c.wins), String.valueOf(c.score)));
        }
        return values;
    }

    private static void makeBold(String range, int sheetId) throws IOException {
        List<Request> requests = new ArrayList<>();
        TextFormat format = new TextFormat().setBold(true);
        CellFormat cellFormat = new CellFormat().setTextFormat(format);
        int startColumn = range.charAt(0)-65;
        int startRow = Integer.parseInt(String.valueOf(range.charAt(1))) - 1;
        int endColumn = range.charAt(3) - 65 + 1;
        int endRow = Integer.parseInt(String.valueOf(range.charAt(4)));
        GridRange gridRange = new GridRange().setStartColumnIndex(startColumn).setStartRowIndex(startRow)
                .setEndColumnIndex(endColumn).setEndRowIndex(endRow).setSheetId(sheetId);
        CellData cellData = new CellData().setUserEnteredFormat(cellFormat);
        RepeatCellRequest repeatCellRequest = new RepeatCellRequest().setCell(cellData).setRange(gridRange)
                .setFields("userEnteredFormat.textFormat.bold");
        Request request = new Request().setRepeatCell(repeatCellRequest);
        requests.add(request);
        BatchUpdateSpreadsheetRequest requestBody = new BatchUpdateSpreadsheetRequest();
        requestBody.setRequests(requests);
        sheetsService.spreadsheets().batchUpdate(SHEET_ID, requestBody).execute();
    }
    private static void borderExecution(Integer sheetId) throws IOException {
        List<Request> requests = new ArrayList<>();
        Border border2 = new Border().setWidth(2).setStyle("SOLID");
        GridRange gridRange = new GridRange().setStartColumnIndex(4).setStartRowIndex(0)
                .setEndColumnIndex(5).setEndRowIndex(1).setSheetId(sheetId);
        UpdateBordersRequest updateBordersRequest = new UpdateBordersRequest().setRange(gridRange)
                .setBottom(border2).setLeft(border2).setRight(border2).setTop(border2);
        Request request = new Request().setUpdateBorders(updateBordersRequest);
        requests.add(request);
        GridRange gridRange2 = new GridRange().setStartColumnIndex(5).setStartRowIndex(0)
                .setEndColumnIndex(6).setEndRowIndex(1).setSheetId(sheetId);
        UpdateBordersRequest updateBordersRequest2 = new UpdateBordersRequest().setRange(gridRange2)
                .setBottom(border2).setLeft(border2).setRight(border2).setTop(border2);
        Request request2 = new Request().setUpdateBorders(updateBordersRequest2);
        requests.add(request2);
        BatchUpdateSpreadsheetRequest requestBody = new BatchUpdateSpreadsheetRequest();
        requestBody.setRequests(requests);
        sheetsService.spreadsheets().batchUpdate(SHEET_ID, requestBody).execute();
    }

    private static void initLayout() throws IOException, GeneralSecurityException {
        write("Main!A1:F1", Arrays.asList(
                Arrays.asList("input names below, number here", "sport", "input 1 where applies",
                        "", "type action to execute", "execution parameter")));
        write("Main!B2:B4", Arrays.asList(Arrays.asList("chess"),
                Arrays.asList("table tennis"), Arrays.asList("football")));
        write("Main!E2:F10", Arrays.asList(
                Arrays.asList("Actions", "Description"),
                Arrays.asList("init", "Fills in initial layout of the program (this one)"),
                Arrays.asList("make str", "Creates initial Olympic tournament structure"),
                Arrays.asList("update str", "Updates the Olympic tournament with given scores"),
                Arrays.asList("make robin", "Creates tournament with Round Robin structure"),
                Arrays.asList("update robin", "Updates the Round Robin tournament with given scores"),
                Arrays.asList("stats", "Creates new tab with current stats"),
                Arrays.asList("total stats", "Updates or creates a summary of stats tab"),
                Arrays.asList("reset stats", "Resets total stats")));
        makeBold("A1:D1", 0);
        makeBold("E2:F2", 0);
        borderExecution(0);
    }

    private static void currentStats() throws IOException, GeneralSecurityException {
        List<List<Object>> values = new ArrayList<>();
        values.add(Arrays.asList("Name", "Wins", "Score"));
        values.addAll(contestantsToList(contestants));
        write("Stats!A1:C" + (contestants.size() + 1), values);
    }

    private static void totalStats() throws IOException, GeneralSecurityException {
        Map<String, Contestant> allContestants = new HashMap<>();
        List<List<Object>> values, total, result = new ArrayList<>();
        values = read("Stats!A1:C" + MAX_CONTESTANTS);
        total = read("Total stats!A1:C" + MAX_CONTESTANTS);
        if (total == null || total.size() < 2){
            write("Total stats!A1:C" + (values.size() + 1), values);
            return;
        }
        for (List row : total) {
            if (!row.get(0).equals("Name")) {
                allContestants.put((String) row.get(0), new Contestant((String) row.get(0),
                        Integer.parseInt((String) row.get(2)),0,
                        Integer.parseInt((String) row.get(1)), new Pair<>(0, 0)));
            }
        }
        for (int i = 1; i < values.size(); i++){
            allContestants.get((String) values.get(i).get(0)).wins += Integer.parseInt((String) values.get(i).get(1));
            allContestants.get((String) values.get(i).get(0)).score += Integer.parseInt((String) values.get(i).get(2));
        }

        result.add(Arrays.asList("Name", "Wins", "Score"));
        result.addAll(contestantsToList(allContestants));
        write("Total stats!A1:C" + (allContestants.size() + 1), result);
        write("Total stats!E1:F1", Arrays.asList(Arrays.asList(
                "type action to execute", "execution parameter")));
        borderExecution(4);
    }

    private static void resetTotalStats() throws IOException, GeneralSecurityException {
        List<List<Object>> empty = new ArrayList<>();
        for (int i = 0; i < MAX_CONTESTANTS - 1; i++){
            empty.add(Arrays.asList("", "", ""));
        }
        write("Total stats!A2:C" + MAX_CONTESTANTS, empty);
    }

    private static void initStructure() throws IOException, GeneralSecurityException {
        String range;
        List<List<Object>> names = checkNames();
        if (names.size() == 0){
            return;
        }
        //System.out.println(Math.log(names.size()) / Math.log(2));
        List<List<Object>> values = new ArrayList<> ();
        for (int i = 0; i < names.size() * 2; i++) {
            if (i % 2 == 0) {
                values.add(Arrays.asList(names.get(i / 2).get(0)));
            }
            else{
                values.add(Arrays.asList(""));
            }
        }
        range = "Tournament structure!a1:a";
        write(range + (2 * names.size()), values);
        write("Tournament structure!E1:F1", Arrays.asList(Arrays.asList(
                "type action to execute", "execution parameter")));

        List<Request> requests = new ArrayList<>();
        Border border = new Border().setWidth(1).setStyle("SOLID");
        for (int i = 0; i < 2 * names.size(); i+=2) {
            GridRange gridRange = new GridRange().setStartColumnIndex(0).setStartRowIndex(i)
                    .setEndColumnIndex(1).setEndRowIndex(i + 1).setSheetId(1); // should be 1
            UpdateBordersRequest updateBordersRequest = new UpdateBordersRequest().setRange(gridRange)
                    .setBottom(border).setLeft(border).setRight(border).setTop(border);
            Request request = new Request().setUpdateBorders(updateBordersRequest);
            requests.add(request);
        }
        borderExecution(1);
        BatchUpdateSpreadsheetRequest requestBody = new BatchUpdateSpreadsheetRequest();
        requestBody.setRequests(requests);
        sheetsService.spreadsheets().batchUpdate(SHEET_ID, requestBody).execute();
    }

    private static void updateStructure() throws IOException, GeneralSecurityException {
        List<List<Object>> struct = read("tournament structure!A1:E15");
        int step = 0;
        System.out.println(struct);
        for (int i = 0; i < 4; i++){
            if(i == 0){
                if (!struct.get(0).get(0).equals("")) {
                    step++;
                }
            }
            else if (!struct.get((int) Math.pow(2, i) - 1).get(i).equals("")){
                step++;
            }
        }
        System.out.println(step);
        // name_positions = pow(2, step - 1) - 1 + pow(2, step) * n
        int pos = (int) Math.pow(2, step - 1) - 1;
        boolean have_pair = false;
        Contestant last = new Contestant("");
        Contestant current = new Contestant("");
        Pair<Integer, Integer> winner_pos;
        while (!struct.get(pos).get(step).equals("") || pos > 14){
            System.out.println(pos);
            if (have_pair){
                have_pair = false;
                winner_pos = new Pair<>(pos - ((int) Math.pow(2, step) / 2), step);
                String range = "Tournament structure!";
                range += Character.toString((char)(winner_pos.getValue() + 65));
                range += Integer.toString(winner_pos.getKey() + 1);
                System.out.println(range);
                current = contestants.get((String) struct.get(pos).get(step - 1));
                current.scoreNow = Integer.parseInt((String) struct.get(pos).get(step));
                current.score += current.scoreNow;
                if (last.scoreNow > current.scoreNow){
                    write(range, Arrays.asList(Arrays.asList(last.name)));
                    last.wins++;
                }else{
                    write(range, Arrays.asList(Arrays.asList(current.name)));
                    current.wins++;
                }
                System.out.println("pair made");
            }else{
                have_pair = true;
                last = contestants.get((String) struct.get(pos).get(step - 1));
                last.scoreNow = Integer.parseInt((String) struct.get(pos).get(step));
                last.score += last.scoreNow;
            }
            pos += Math.pow(2, step);
        }
    }

    private static void initRobin() throws IOException, GeneralSecurityException {
        List<List<Object>> names = checkNames();
        if (names.size() == 0){
            return;
        }
        List<List<Object>> values = new ArrayList<> ();
        for (List<Object> objects : names) {
            values.add(Arrays.asList(objects.get(0)));
        }
        write("Robin structure!a2:a" + (names.size() + 1), values);
        List<Object> horValuesTmp = new ArrayList<> ();
        for (List<Object> name : names) {
            horValuesTmp.add(name.get(0));
        }
        horValuesTmp.add("score");
        horValuesTmp.add("rank");
        List<List<Object>> horValues;
        horValues = Arrays.asList(horValuesTmp);
        write("Robin structure!b1:" + (char)(names.size() + 65 + 2) + "1", horValues);
        makeBold("A1:K1", 2);
        makeBold("A2:A9", 2);
        // write("Robin structure!E1:F1", Arrays.asList(Arrays.asList("type action to execute", "execution parameter")));
    }

    private static void updateRobin() throws IOException, GeneralSecurityException {
        List<List<Object>> names = checkNames();
        if (names.size() == 0){
            return;
        }
        List<List<Object>> struct = read("Robin structure!A1:K11");
        Pattern fPattern = Pattern.compile(".*,");
        Pattern sPattern = Pattern.compile(",.*");
        List<Integer> scores = new ArrayList<>();
        for (int i = 1; i < struct.size(); i++){
            List <Object> row = struct.get(i);
            Contestant con = contestants.get((String) row.get(0));
            con.scoreNow = 0;
            for (int j = 1; j < row.size(); j++){
                Matcher fMatcher = fPattern.matcher((String) row.get(j));
                if(fMatcher.find()) {
                    String fScore = ((String) row.get(j)).substring(fMatcher.start(), fMatcher.end());
                    con.scoreNow += Integer.parseInt(fScore.substring(0, fScore.length() - 1));
                }
            }
            for (int j = 1; j < i; j++){
                Matcher sMatcher = sPattern.matcher((String) struct.get(j).get(i));
                if(sMatcher.find()) {
                    String sScore = ((String) struct.get(j).get(i)).substring(sMatcher.start(), sMatcher.end());
                    con.scoreNow += Integer.parseInt(sScore.substring(1));
                }
            }
            scores.add(con.scoreNow);
            con.score = con.scoreNow;
            write("Robin structure!" + (char)(names.size() + 65 + 1) + (i + 1),
                    Arrays.asList(Arrays.asList(con.score)));
        }
        List<Integer> ranks = Lists.reverse(argsort(scores));
        List<List<Object>> ranksStruct = new ArrayList<>();
        for (int i = 0; i < ranks.size(); i++){
            ranksStruct.add(Arrays.asList());
        }
        for (int i = 0; i < ranks.size(); i++){
            ranksStruct.set(ranks.get(i), Arrays.asList(i + 1));
        }
        write("Robin structure!" + (char)(names.size() + 65 + 2) + "2" + ":"
                + (char)(names.size() + 65 + 2) + (names.size() + 1), ranksStruct);
    }

    // lol there are no parameters with default values
    // have to use overloading instead
    private static List<List<Object>> act(String action, String range, List<List<Object>> values)
            throws IOException, GeneralSecurityException {
        switch (action) {
            case "read":
                values = read(range);
                System.out.println("values READ");
                break;
            case "write":
                write(range, values);
                System.out.println("values WRITTEN");
                break;
            case "init":
                renameTab();
                initLayout();
                System.out.println("LAYOUT LOADED");
                break;
            case "make str":
                addTab("Tournament structure", 1);
                initStructure();
                System.out.println("STRUCTURE MADE");
                break;
            case "update str":
                List<List<Object>> spaces = new ArrayList<>();
                for (int i = 0; i < 14; i++){
                    spaces.add(Arrays.asList(" "));
                }
                write("Tournament structure!e2:e15", spaces);
                updateStructure();
                System.out.println("STR UPDATED");
                break;
            case "make robin":
                addTab("Robin structure", 2);
                initRobin();
                System.out.println("ROBIN MADE");
                break;
            case "update robin":
                updateRobin();
                System.out.println("ROBIN UPDATED");
                break;
            case "stats":
                addTab("Stats", 3);
                currentStats();
                System.out.println("STATS MADE");
                break;
            case "total stats":
                addTab("Total stats", 4);
                totalStats();
                System.out.println("TOTAL STATS MADE");
                break;
            case "reset stats":
                resetTotalStats();
                System.out.println("TOTAL STATS RESET");
                break;
            default:
                System.out.println("incorrect input");
                System.err.println("incorrect input");
        }
        return values;
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        String action, range="", strValues="";
        List<List<Object>> receivedValues;
        Pair<String, String> exeParams;
        if (SHEET_ID.equals("")){
            SHEET_ID = scanner.nextLine();
            sheetsService = getSheetsService();
            System.out.println("authorization complete");
            List<List<Object>> emptyList = null;
            act("init", "", emptyList);

        }else {
            sheetsService = getSheetsService();
        }
        while (true) {
            System.out.println("enter your action, range and values if any:");
            action = scanner.nextLine();
            if (action.equals("exit")){
                break;
            }
            System.out.println("processing");
            List<List<Object>> values = Arrays.asList(Arrays.asList(strValues));
            if (action.equals("wait")){
                exeParams = waitExecute();
                action = exeParams.getKey();
                range = exeParams.getValue();
            }
            receivedValues = act(action, range, values);
            System.out.println(receivedValues);
        }
    }
}
