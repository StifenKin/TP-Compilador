package lyc.compiler.files;

import lyc.compiler.table.SymbolEntry;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static lyc.compiler.table.SymbolTableManager.symbolTable;

public class SymbolTableGenerator implements FileGenerator{

    private final int columnNameWidth = 52;
    private final int columnTypeWidth = 20;
    private final int columnValueWidth = 52;
    private final int columnLengthWidth = 24;

    @Override
    public void generate(FileWriter fileWriter) throws IOException {
        List<String> keys = new ArrayList<>(symbolTable.keySet());
        Collections.sort(keys);
        Collections.reverse(keys);

        fileWriter.write("+----------------------------------------------------------------- TABLA DE SIMBOLOS ---------------------------------------------------------------+\n");
        fileWriter.write("|\t\t\t\t\t\tNOMBRE\t\t\t\t\t\t|\t\tTIPO\t\t|\t\t\t\t\t\tVALOR\t\t\t\t\t\t|\t\tLONGITUD\t\t|\n");
        fileWriter.write("+---------------------------------------------------------------------------------------------------------------------------------------------------+\n");

        for(String key : keys){
            SymbolEntry value = symbolTable.get(key);

            String dataTypeStr = value.getDataType() == null ? "" : value.getDataType().getName();

            try {
                fileWriter.write("|" + key + tabCalculator(key, columnNameWidth) + "|" + dataTypeStr + tabCalculator(dataTypeStr, columnTypeWidth) + "|" + value.getValue() + tabCalculator(value.getValue(), columnValueWidth) + "|" + value.getLength() + tabCalculator(value.getLength(), columnLengthWidth) + "|\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        fileWriter.write("+---------------------------------------------------------------------------------------------------------------------------------------------------+");
    }

    private String tabCalculator(String value, int columnWidth){
        String tab = "\t";
        int repeatTimes = roundNumber((float)(columnWidth - value.length()) / 4);

        return tab.repeat(repeatTimes);
    }

    private int roundNumber(float num){
        float diff = num - (int)num;
        if(diff >= 0.5)
            return (int)num+1;
        else
            return (int)num;
    }
}

