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
import javafx.util.Pair;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.*;

public class TournamentManager {
    private static String SHEET_ID = "1aQqQ9ldMDDjqU0rG1en9B5_tnnX_cpJr1ZHmi5k29ac";
    private static Sheets sheetsService;
    private static final String APPLICATION_NAME = "Tournament Manager";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/client_id.json";
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

    private static List<List<Object>> read(String range) throws IOException, GeneralSecurityException {
        if (!range.contains("!")){
            System.out.println(range);
            throw new IllegalArgumentException("sheet not specified");
        }
        range = range.toUpperCase();
        sheetsService = getSheetsService();
        ValueRange response = sheetsService.spreadsheets().values().get(SHEET_ID, range).execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()){
            System.out.println("No data found");
        }
        return values;
    }

    private static void write(String range, List<List<Object>> values) throws IOException, GeneralSecurityException {
        if (!range.contains("!")){
            System.out.println(range);
            throw new IllegalArgumentException("sheet not specified");
        }
        range = range.toUpperCase();
        sheetsService = getSheetsService();
        ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values().update(SHEET_ID, range, body).setValueInputOption("RAW").execute();
    }

    private static Pair<String, String> waitExecute()
            throws InterruptedException, IOException, GeneralSecurityException {
        sheetsService = getSheetsService();
        String main_sheet = "Main!", str_sheet = "Tournament Structure!";
        List<List<Object>> values_main = read(main_sheet + "e1");
        List<List<Object>> values_str = read(str_sheet + "e1");
        String action, param;
        while (values_main.get(0).get(0).equals("type action to execute") &&
                values_str.get(0).get(0).equals("type action to execute")) {
            Thread.sleep(1000);
            sheetsService = getSheetsService();
            try {
                values_main = read(main_sheet + "e1:f1");
                values_str = read(str_sheet + "e1:f1");
            }catch (java.net.SocketTimeoutException e){
                System.err.println("Caught TimeoutException");
            }
        }
        if (!values_main.get(0).get(0).equals("type action to execute")) {
            write(main_sheet + "e1:f1", Arrays.asList(Arrays.asList("type action to execute", "execution parameter")));
            action = (String) values_main.get(0).get(0);
            param = main_sheet + values_main.get(0).get(1);
        }
        else{
            write(str_sheet + "e1:f1", Arrays.asList(Arrays.asList("type action to execute", "execution parameter")));
            action = (String) values_str.get(0).get(0);
            param = str_sheet + values_str.get(0).get(1);
        }
        return new Pair<>(action, param);
    }

    private static void initLayout() throws IOException, GeneralSecurityException {
        sheetsService = getSheetsService();
        write("Main!A1:F1", Arrays.asList(
                Arrays.asList("input names below, number here", "sport", "input 1 where applies",
                        "", "type action to execute", "execution parameter")));
        write("Main!B2:B4", Arrays.asList(Arrays.asList("chess"),
                Arrays.asList("table tennis"), Arrays.asList("football")));
    }

    private static void getNames() throws IOException, GeneralSecurityException {
        sheetsService = getSheetsService();
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
            contestants.put((String) row.get(0),
                    new Contestant((String) row.get(0)));
        }
    }

    private static void addTab(String title) throws IOException, GeneralSecurityException {
        boolean flag = false;
        sheetsService = getSheetsService();
        Spreadsheet ssheet = sheetsService.spreadsheets().get(SHEET_ID).execute();
        for (Sheet sheet : ssheet.getSheets()) {
            if (sheet.getProperties().getTitle().equals(title)){
                System.out.printf("sheet %s already exists\n", title);
                flag = true;
            }
        }
        if (!flag) {
            List<Request> requests = new ArrayList<>();
            AddSheetRequest addSheet = new AddSheetRequest().setProperties(new SheetProperties().setTitle(title));
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

    private static void initStats() throws IOException, GeneralSecurityException {
        sheetsService = getSheetsService();
        List<List<Object>> names = checkNames();
        write("Stats!A2:A" + (names.size() + 1), names);

    }

    private static void initStructure() throws IOException, GeneralSecurityException {
        String range;
        sheetsService = getSheetsService();
        List<List<Object>> names = checkNames();
        if (names.size() == 0){
            return;
        }
        //System.out.println(Math.log(names.size()) / Math.log(2));
        List<List<Object>> values = new ArrayList<> ();
        for (int i = 0; i < names.size() * 2; i++) {
            if (i % 2 == 0) {
                values.add(Arrays.asList(names.get(i / 2)));
            }
            else{
                values.add(Arrays.asList(""));
            }
        }
        range = "Tournament structure!a1:a";
        write(range + (2 * names.size()), values);
        write("Tournament structure!E1:F1", Arrays.asList(Arrays.asList("type action to execute", "execution parameter")));
    }

    private static void updateStructure() throws IOException, GeneralSecurityException {
        sheetsService = getSheetsService();
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
                if (last.scoreNow > Integer.parseInt((String) struct.get(pos).get(step))){
                    write(range, Arrays.asList(Arrays.asList(last.name)));
                }else{
                    write(range, Arrays.asList(Arrays.asList(struct.get(pos).get(step - 1))));
                }
                System.out.println("pair made");
            }else{
                have_pair = true;
                last = contestants.get((String) struct.get(pos).get(step - 1));
                last.scoreNow = Integer.parseInt((String) struct.get(pos).get(step));
            }
            pos += Math.pow(2, step);
        }
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
                addTab("Tournament structure");
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
            case "init stats":
                addTab("Stats");
                initStats();
                System.out.println("STATS MADE");
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
        while (true) {
            System.out.println("enter your action, range and values if any:");
            action = scanner.nextLine();
            if (action.equals("exit")){
                break;
            }
            if (action.equals("read") || action.equals("write")) {
                range = scanner.nextLine().toUpperCase();
            }
            if (action.equals("write")) {
                strValues = scanner.nextLine();
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
