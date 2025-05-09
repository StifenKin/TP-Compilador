package lyc.compiler.table;

import java.util.HashMap;

public class SymbolTableManager {

    public static HashMap<String, SymbolEntry> symbolTable = new HashMap<>();

    public HashMap<String, SymbolEntry> getSymbolTable() {
        return symbolTable;
    }

    public static void insertInTable(SymbolEntry entry){
            symbolTable.put(entry.getName().replace(" ", "_"), entry);
    }

    public static void setDataTypeInTable(String key, DataType dataType){
        SymbolEntry entry = symbolTable.get(key);
        entry.setDataType(dataType);
    }

    public static void removeFromTable(String entryName) {
        // Reemplazar espacios por guiones bajos para mantener coherencia con insertInTable


        if (symbolTable.containsKey(entryName)) {
            symbolTable.remove(entryName);
            System.out.println("Removed symbol: " + entryName);
        } else {
            System.out.println("Symbol not found: " + entryName);
        }
    }


    public static boolean existsInTable(String entryName){
        return symbolTable.containsKey(entryName);
    }

}


