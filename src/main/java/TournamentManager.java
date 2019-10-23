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
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

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
        range = range.toUpperCase();
        sheetsService = getSheetsService();
        ValueRange response = sheetsService.spreadsheets().values().get(SHEET_ID, range).execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()){
            System.out.println("No data found");
        } else {
            for (List row : values) {
                System.out.println(row);
            }
        }
        return values;
    }

    private static void write(String range, List<List<Object>> values) throws IOException, GeneralSecurityException {
        range = range.toUpperCase();
        sheetsService = getSheetsService();
        ValueRange body = new ValueRange().setValues(values);
        UpdateValuesResponse result = sheetsService.spreadsheets().values().update(SHEET_ID, range, body)
                .setValueInputOption("RAW").execute();
        System.err.println(result);
    }

    private static void initLayout() throws IOException, GeneralSecurityException {
        sheetsService = getSheetsService();
        write("A1:E1", Arrays.asList(
                Arrays.asList("input names below, number here", "sport", "input 1 where applies",
                        "input action", "type \"e\" to execute")));
        write("B2:B4", Arrays.asList(Arrays.asList("chess"),
                Arrays.asList("table tennis"), Arrays.asList("football")));
    }

    private static List<String> getNames() throws IOException, GeneralSecurityException {
        sheetsService = getSheetsService();
        List<List<Object>> range = read("a1");
        int numRange = Integer.parseInt((String) range.get(0).get(0));
        System.out.println(numRange);
        List<List<Object>> values = read("a2:a"+Integer.toString(numRange + 1));
        List<String> names = new ArrayList<> ();
        for (List row : values){
            names.add((String)row.get(0));
        }
        System.out.println(names);
        return names;
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
                initLayout();
                System.out.println("LAYOUT LOADED");
                break;
        }
        return values;
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        Scanner scanner = new Scanner(System.in);
        String action, range="", strValues="";
        List<String> names;
        List<List<Object>> receivedValues;
        while (true) {
            System.out.println("enter your action, range and values if any:");
            action = scanner.nextLine();
            if (action.equals("exit")){
                break;
            }
            if (action.equals("get names")){
                names = getNames();
            }
            if (action.equals("read") || action.equals("write")) {
                range = scanner.nextLine().toUpperCase();
                range = "Sheet1!" + range;
            }
            if (action.equals("write")) {
                strValues = scanner.nextLine();
            }
            System.out.println("processing");
            List<List<Object>> values = Arrays.asList(Arrays.asList(strValues));
            receivedValues = act(action, range, values);
            System.out.println(receivedValues);
        }
    }
}
