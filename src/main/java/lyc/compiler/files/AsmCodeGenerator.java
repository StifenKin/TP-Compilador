
package lyc.compiler.files;

import lyc.compiler.tree.*;
import lyc.compiler.table.SymbolEntry;
import lyc.compiler.table.SymbolTableManager;
import lyc.compiler.table.DataType;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class AsmCodeGenerator implements FileGenerator {
    private static int labelCounter = 0;
    private static StringBuilder dataSection = new StringBuilder();
    private static StringBuilder codeSection = new StringBuilder();

    private static int tempCount = 0;
    private static int labelCount = 0;
    private static final Map<String, Boolean> declaredTemps = new HashMap<>();
    private static int intSlotCount = 0;

    private static String defineIntSlot() {
        intSlotCount++;
        String name = "@intSlot" + intSlotCount;
        dataSection.append(name).append(" DD 0\n");
        return name;
    }

    @Override
    public void generate(FileWriter fileWriter) throws IOException {
        Integer root = ASTManager.getRoot();

        dataSection.setLength(0);
        codeSection.setLength(0);
        declaredTemps.clear();
        tempCount = 0;
        labelCount = 0;

        genDataHeader();
        genUserVars();
        dataSection.append("@c    DD 0.0\n\n");

        dataSection.append("@sum  DD 0.0\n\n");

        dataSection.append("@mult DD 1.0\n\n");

        dataSection.append("@aux  DD 0.0\n\n");


        genCodeHeader();
        genStatement(root);
        genCodeFooter();

        StringBuilder finalAsm = new StringBuilder();
        finalAsm.append(dataSection);
        finalAsm.append(codeSection);

        fileWriter.write(finalAsm.toString());
    }

    private static void genDataHeader() {
        dataSection.append("; *************** SECCION DE DATOS ***************\n");
        dataSection.append(".MODEL LARGE\n");
        dataSection.append(".386\n");
        dataSection.append(".STACK 200h.DATA\n");
    }

    private static void genUserVars() {
        for (Map.Entry<String, SymbolEntry> entry : SymbolTableManager.symbolTable.entrySet()) {
            String nombre = entry.getKey();
            SymbolEntry sym = entry.getValue();
            if (sym == null || sym.getDataType() == null)
                sym.setDataType(DataType.FLOAT_TYPE);
            switch (sym.getDataType()) {
                case INTEGER_TYPE:
                    dataSection.append(nombre).append(" DD 0\n");
                    break;
                case FLOAT_TYPE:
                    dataSection.append(nombre).append(" DD 0.0\n");
                    break;
                case STRING_TYPE:
                    dataSection.append(nombre).append(" DB 256 DUP (?)\n");
                    break;
                default:
                    dataSection.append(nombre).append(" DD 0.0\n");
                    break;
            }
        }
        dataSection.append("\n");
    }

    private static void genCodeHeader() {
        codeSection.append("; *************** SECCION DE CODIGO ***************\n");
        codeSection.append(".CODE\n");
        codeSection.append("mov AX,@DATA\n");
        codeSection.append("mov DS,AX\n");
        codeSection.append("mov ES,AX\n");
    }

    private static void genCodeFooter() {
        codeSection.append("; Fin del programa\n");
        codeSection.append("mov ax,4c00h\n");
        codeSection.append("int 21h\n");
        codeSection.append("End\n");
    }

    private static void genStatement(int index) {
        if (index == 0) return;
        Nodo nodo = GestorNodos.obtenerNodo(index);
        if (nodo == null) return;

        String val = nodo.getValor();
        int leftIdx = getIndex(nodo.getIzquierdo());
        int rightIdx = getIndex(nodo.getDerecho());

        switch (val) {
            case ";": genStatement(leftIdx); genStatement(rightIdx); break;
            case "if": genIf(leftIdx, rightIdx); break;
            case "while": genWhile(leftIdx, rightIdx); break;
            case "=": genAssign(leftIdx, rightIdx); break;
            default: genExpr(index); break;
        }
    }

    private static String genExpr(int index) {
        if (index == 0) return "";
        Nodo nodo = GestorNodos.obtenerNodo(index);
        if (nodo == null) return "";

        String val = nodo.getValor();

        switch(val) {
            case "+": case "-": case "*": case "/": case "%":
                return genBinaryFloatOp(val, getIndex(nodo.getIzquierdo()), getIndex(nodo.getDerecho()));
            case "==": case "!=": case "<": case ">": case "<=": case ">=":
                return genComparison(val, getIndex(nodo.getIzquierdo()), getIndex(nodo.getDerecho()));
            case "reorder":
                return genReorder(nodo);
            case "concat":
                return genSliceAndConcat(nodo);
            default:
                return genLiteralOrVar(val);
        }
    }

    private static String genReorder(Nodo nodo) {
        codeSection.append("; --- REORDER ---\n");

        Nodo argsNode = nodo.getIzquierdo();
        Nodo configNode = nodo.getDerecho();

        List<Integer> exprIndices = new ArrayList<>();
        recolectarExpresiones(argsNode, exprIndices);

        List<String> exprTemporales = new ArrayList<>();
        for (Integer exprIdx : exprIndices) {
            exprTemporales.add(genExpr(exprIdx));
        }

        String dirTemp = genExpr(getIndex(configNode.getIzquierdo()));  // bool
        String pivotTemp = genExpr(getIndex(configNode.getDerecho()));  // int

        codeSection.append("\t; Guardar dirección y pivote\n");
        codeSection.append("\tFLD [" + dirTemp + "]\n");
        codeSection.append("\tFSTP [@dir]\n");

        codeSection.append("\tFLD [" + pivotTemp + "]\n");
        codeSection.append("\tFSTP [@pivot]\n");

        String lblLeft = "REORDER_LEFT_" + labelCounter;
        String lblRight = "REORDER_RIGHT_" + labelCounter;
        String lblEnd = "REORDER_END_" + labelCounter;
        labelCounter++;

        // Comparar @dir == 1.0 (hacia izquierda)
        codeSection.append("\tFLD [@dir]\n");
        codeSection.append("\tFLD1\n"); // carga 1.0
        codeSection.append("\tFCOMP\n");
        codeSection.append("\tFSTSW AX\n");
        codeSection.append("\tSAHF\n");
        codeSection.append("\tJE " + lblLeft + "\n"); // si dir == 1 → izquierda
        codeSection.append("\tJMP " + lblRight + "\n");

        // --- IZQUIERDA ---
        codeSection.append(lblLeft + ":\n");
        codeSection.append("\tFLDZ\n\tFSTP [@i]\n");
        codeSection.append(lblLeft + "_loop:\n");

        codeSection.append("\tFLD [@i]\n");
        codeSection.append("\tFLD [@pivot]\n");
        codeSection.append("\tFCOMP\n");
        codeSection.append("\tFSTSW AX\n");
        codeSection.append("\tSAHF\n");
        codeSection.append("\tJAE " + lblEnd + "\n"); // i >= pivot → fin

        for (int i = 0; i < exprTemporales.size(); i++) {
            codeSection.append("; expr[" + i + "] = " + exprTemporales.get(i) + "\n");
        }

        for (int i = 0; i < exprTemporales.size(); i++) {
            String origen = exprTemporales.get(i);
            codeSection.append("\t; Simular copia: FLD " + origen + " → @tmp" + (200 + i) + "\n");
            codeSection.append("\tFLD [" + origen + "]\n");
            codeSection.append("\tFSTP [@tmp" + (200 + i) + "]\n");
        }

        // Incrementar i
        codeSection.append("\tFLD [@i]\n");
        codeSection.append("\tFLD1\n");
        codeSection.append("\tFADD\n");
        codeSection.append("\tFSTP [@i]\n");
        codeSection.append("\tJMP " + lblLeft + "_loop\n");

        // --- DERECHA ---
        codeSection.append(lblRight + ":\n");
        codeSection.append("\tFLD [@pivot]\n");
        codeSection.append("\tFSTP [@i]\n");
        codeSection.append(lblRight + "_loop:\n");

        codeSection.append("\tFLD [@i]\n");
        codeSection.append("\tFLD " + exprTemporales.size() + ".0\n");
        codeSection.append("\tFCOMP\n");
        codeSection.append("\tFSTSW AX\n");
        codeSection.append("\tSAHF\n");
        codeSection.append("\tJAE " + lblEnd + "\n"); // i >= len → fin

        for (int i = 0; i < exprTemporales.size(); i++) {
            codeSection.append("; (derecha) expr[" + i + "] = " + exprTemporales.get(i) + "\n");
        }

        for (int i = 0; i < exprTemporales.size(); i++) {
            String origen = exprTemporales.get(i);
            codeSection.append("\tFLD [" + origen + "]\n");
            codeSection.append("\tFSTP [@tmp" + (300 + i) + "]\n");
        }

        // Incrementar i
        codeSection.append("\tFLD [@i]\n");
        codeSection.append("\tFLD1\n");
        codeSection.append("\tFADD\n");
        codeSection.append("\tFSTP [@i]\n");
        codeSection.append("\tJMP " + lblRight + "_loop\n");

        codeSection.append(lblEnd + ":\n");

        return "";
    }



    private static void recolectarExpresiones(Nodo nodo, List<Integer> resultados) {
        if (nodo == null) return;

        if (",".equals(nodo.getValor())) {
            recolectarExpresiones(nodo.getIzquierdo(), resultados);
            recolectarExpresiones(nodo.getDerecho(), resultados);
        } else {
            resultados.add(nodo.getIndice());
        }
    }

    private static String genSliceAndConcat(Nodo nodo) {
        codeSection.append("; --- SLICE AND CONCAT ---\n");

        Nodo string2Nodo = nodo.getIzquierdo();
        Nodo applyNodo = nodo.getDerecho();

        if (applyNodo == null || string2Nodo == null) return "";

        Nodo sliceNodo = applyNodo.getIzquierdo();
        Nodo string1Nodo = applyNodo.getDerecho();

        if (sliceNodo == null || string1Nodo == null) return "";

        Nodo start = sliceNodo.getIzquierdo();
        Nodo end = sliceNodo.getDerecho();

        codeSection.append("; Slice de ").append(string1Nodo.getValor())
            .append(" desde ").append(start.getValor())
            .append(" hasta ").append(end.getValor()).append("\n");

        codeSection.append("; Concatenado con ").append(string2Nodo.getValor()).append("\n");

        String result = newTemp();
        codeSection.append("; Resultado en ").append(result).append("\n");

        return result;
    }

    private static void genIf(int condIdx, int bodyIdx) {
        codeSection.append("; --- IF statement ---\n");
        String condTemp = genExpr(condIdx);

        String labelElse = newLabel("ELSE\n");
        String labelEnd = newLabel("ENDIF\n");

        codeSection.append("	FLD [").append(condTemp).append("]\n");
        codeSection.append("	FTST	FSTSW AX	SAHF\n");
        codeSection.append("	JE ").append(labelElse).append("\n");

        Nodo body = GestorNodos.obtenerNodo(bodyIdx);
        if (body != null && "cuerpo".equals(body.getValor())) {
            genStatement(getIndex(body.getIzquierdo()));
            codeSection.append("	jmp ").append(labelEnd).append("\n");
            codeSection.append(labelElse).append(":\n");
            genStatement(getIndex(body.getDerecho()));
        } else {
            genStatement(bodyIdx);
            codeSection.append(labelElse).append(":\n");
        }
        codeSection.append(labelEnd).append(":\n");
    }

    private static void genWhile(int condIdx, int bodyIdx) {
        codeSection.append("; --- WHILE ---\n");
        String labelStart = newLabel("WHILE\n");
        String labelEnd = newLabel("ENDWHILE\n");

        codeSection.append(labelStart).append(":\n");
        String condTemp = genExpr(condIdx);

        codeSection.append("	FLD [").append(condTemp).append("]\n");
        codeSection.append("	FTST	FSTSW AX	SAHF\n");
        codeSection.append("	JE ").append(labelEnd).append("\n");

        genStatement(bodyIdx);
        codeSection.append("	jmp ").append(labelStart).append("\n");
        codeSection.append(labelEnd).append(":\n");
    }

    private static void genAssign(int leftIdx, int rightIdx) {
        Nodo var = GestorNodos.obtenerNodo(leftIdx);
        if (var == null) return;

        String exprTemp = genExpr(rightIdx);
        codeSection.append("	FLD [").append(exprTemp).append("]\n");
        codeSection.append("	FSTP [").append(var.getValor()).append("]\n");
    }

    private static String genLiteralOrVar(String val) {
        String tmp = newTemp();
        if (esNumero(val)) {
            String lit = defineLiteral(val);
            codeSection.append("	FLD [").append(lit).append("]\n");
        } else {
            codeSection.append("	FLD [").append(val).append("]\n");
        }
        codeSection.append("	FSTP [").append(tmp).append("]\n");
        return tmp;
    }

    private static String genBinaryFloatOp(String op, int leftIdx, int rightIdx) {
        String left = genExpr(leftIdx);
        String right = genExpr(rightIdx);
        String result = newTemp();

        codeSection.append("	FLD [").append(right).append("]\n");
        codeSection.append("	FLD [").append(left).append("]\n");
        switch (op) {
            case "+": codeSection.append("	FADD ST0, ST1\n"); break;
            case "-": codeSection.append("	FSUB ST0, ST1\n"); break;
            case "*": codeSection.append("	FMUL ST0, ST1\n"); break;
            case "/": codeSection.append("	FDIV ST0, ST1\n"); break;
        }
        codeSection.append("	FSTP [").append(result).append("]\n");
        return result;
    }

    private static String genComparison(String op, int leftIdx, int rightIdx) {
        String left = genExpr(leftIdx);
        String right = genExpr(rightIdx);
        String result = newTemp();

        String labelTrue = newLabel("CMPTRUE\n");
        String labelEnd = newLabel("CMPEND\n");

        codeSection.append("	FLD [").append(left).append("]\n");
        codeSection.append("	FSUB [").append(right).append("]\n");
        codeSection.append("	FTST	FSTSW AX	SAHF\n");

        switch (op) {
            case "==": codeSection.append("	JNE \n"); break;
            case "!=": codeSection.append("	JE \n"); break;
            case "<":  codeSection.append("	JGE \n"); break;
            case ">":  codeSection.append("	JLE \n"); break;
            case "<=": codeSection.append("	JG \n");  break;
            case ">=": codeSection.append("	JL \n");  break;
        }
        codeSection.append(labelEnd).append("\n");

        codeSection.append(labelTrue).append(":	FLD1	JMP ").append(labelEnd).append("\n");
        codeSection.append(labelEnd).append(":	FSTP ST0	FLDZ	FSTP [").append(result).append("]\n");

        return result;
    }

    private static String newTemp() {
        tempCount++;
        String name = "@tmp" + tempCount;
        if (!declaredTemps.containsKey(name)) {
            dataSection.append(name).append(" DD 0.0\n");
            declaredTemps.put(name, true);
        }
        return name;
    }

    private static String defineLiteral(String val) {
        String name = "_" + val.replace(".", "_\n");
        if (!declaredTemps.containsKey(name)) {
            dataSection.append(name).append(" DD ").append(val).append("\n");
            declaredTemps.put(name, true);
        }
        return name;
    }

    private static String newLabel(String base) {
        labelCount++;
        return base + labelCount;
    }

    private static boolean esNumero(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int getIndex(Nodo nodo) {
        return (nodo == null) ? 0 : nodo.getIndice();
    }
}
