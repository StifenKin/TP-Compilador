package lyc.compiler.files;

import lyc.compiler.tree.*;
import lyc.compiler.table.SymbolEntry;
import lyc.compiler.table.SymbolTableManager;
import lyc.compiler.table.DataType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class AsmCodeGenerator implements FileGenerator {

    // Buffers donde concatenamos código .data y .code
    private static StringBuilder dataSection = new StringBuilder();
    private static StringBuilder codeSection = new StringBuilder();

    // Contadores para nombres únicos en temporales y etiquetas
    private static int tempCount = 0;
    private static int labelCount = 0;

    // Para no volver a declarar un temporal o literal varias veces
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
        fileWriter.write("; no se utiliza.\n");
    }

    /**
     * Metodo principal: genera el archivo .asm con la sección de datos + código.
     * @param outputFile Ruta del archivo .asm
     * @param rootIndex Índice del nodo raíz del AST (blockIdx)
     */
    public static void generateAssembler(String outputFile, int rootIndex) throws IOException {
        // Reiniciar buffers y contadores
        dataSection.setLength(0);
        codeSection.setLength(0);
        declaredTemps.clear();
        tempCount = 0;
        labelCount = 0;

        // Generar encabezados y la sección de datos
        genDataHeader();
        genUserVars();
        // Variables compilador
        dataSection.append("@c    DD 0.0\n");
        dataSection.append("@sum  DD 0.0\n");
        dataSection.append("@mult DD 1.0\n");
        dataSection.append("@aux  DD 0.0\n");

        // Generar la sección de código
        genCodeHeader();

        // Compilar la raíz del AST (postorden)
        genStatement(rootIndex);

        // Finalizar
        genCodeFooter();

        // Unir dataSection + codeSection
        StringBuilder finalAsm = new StringBuilder();
        finalAsm.append(dataSection);
        finalAsm.append(codeSection);

        // 5) Escribir a archivo
        File f = new File(outputFile);
        try (FileWriter writer = new FileWriter(f)) {
            writer.write(finalAsm.toString());
        }

        System.out.println("Assembler generado en: " + f.getAbsolutePath());
    }

    /** Encabezado del .data (modelo, stack, etc.) */
    private static void genDataHeader() {
        dataSection.append("; *************** SECCION DE DATOS ***************\n");
        dataSection.append(".MODEL LARGE\n");
        dataSection.append(".386\n");
        dataSection.append(".STACK 200h\n");
        dataSection.append("\n.DATA\n\n");
    }

    /** Declarar las variables del usuario (tabla de símbolos) */
    private static void genUserVars() {
        for (Map.Entry<String, SymbolEntry> entry : SymbolTableManager.symbolTable.entrySet()) {
            String nombre = entry.getKey();
            SymbolEntry sym = entry.getValue();
            if(sym == null || sym.getDataType() == null)
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

    /** Encabezado de la sección de código */
    private static void genCodeHeader() {
        codeSection.append("; *************** SECCION DE CODIGO ***************\n");
        codeSection.append(".CODE\n\n");
        codeSection.append("mov AX,@DATA\n");
        codeSection.append("mov DS,AX\n");
        codeSection.append("mov ES,AX\n\n");
    }

    /** Footer de la sección de código: salida */
    private static void genCodeFooter() {
        codeSection.append("\n; Fin del programa\n");
        codeSection.append("mov ax,4c00h\n");
        codeSection.append("int 21h\n");
        codeSection.append("End\n");
    }

    /**
     * Recorre (postorden) el subárbol 'index' y genera código (sentencias).
     */
    private static void genStatement(int index) {
        if (index == 0) return;
        Nodo nodo = GestorNodos.obtenerNodo(index);
        if (nodo == null) return;

        String val = nodo.getValor();
        int leftIdx  = getIndex(nodo.getIzquierdo());
        int rightIdx = getIndex(nodo.getDerecho());

        switch(val) {
            case ";":
                genStatement(leftIdx);
                genStatement(rightIdx);
                break;

            case "if":
                genIf(leftIdx, rightIdx);
                break;

            case "while":
                genWhile(leftIdx, rightIdx);
                break;

            case "=":
                genAssign(leftIdx, rightIdx);
                break;

            default:
                genExpr(index);
                break;
        }
    }


    private static void genIf(int condIdx, int bodyIdx) {
        codeSection.append("\n; --- IF statement con else---\n");

        // 1) compilar cond => float (1.0 => true, 0.0 => false)
        String condTemp = genExpr(condIdx);

        // 2) cargar condTemp => comparar con 0
        String labelElse = newLabel("ELSE");
        String labelEnd  = newLabel("END_IF");

        codeSection.append("\tFLD [").append(condTemp).append("]\n");
        codeSection.append("\tFTST\n");
        codeSection.append("\tFSTSW AX\n");
        codeSection.append("\tSAHF\n");
        // si 0 => ZF=1 => salta => else
        codeSection.append("\tJE ").append(labelElse).append("\n");

        // 3) Compilar la parte true
        //   si el nodo "bodyIdx" es "cuerpo", su hijo izquierdo es la parte true
        //   su hijo derecho es la parte false
        Nodo bodyNode = GestorNodos.obtenerNodo(bodyIdx);
        if (bodyNode != null && "cuerpo".equals(bodyNode.getValor())) {
            int truePartIdx = getIndex(bodyNode.getIzquierdo());
            int falsePartIdx = getIndex(bodyNode.getDerecho());

            // compilar parte true
            genStatement(truePartIdx);

            // salto incondicional al fin
            codeSection.append("\tjmp ").append(labelEnd).append("\n");

            // label else
            codeSection.append(labelElse).append(":\n");
            // compilar parte false
            genStatement(falsePartIdx);
        } else {
            // si no hay "cuerpo", es un if sin else
            // compilar "bodyIdx" como un block
            genStatement(bodyIdx);

            codeSection.append(labelElse).append(":\n");
        }

        codeSection.append(labelEnd).append(":\n");
    }

    /** while => left=cond, right=body */
    private static void genWhile(int condIdx, int bodyIdx) {
        codeSection.append("\n; --- WHILE statement ---\n");
        String labelWhile = newLabel("WHILE");
        String labelEnd   = newLabel("ENDWHILE");

        codeSection.append(labelWhile).append(":\n");

        String condTemp = genExpr(condIdx);
        codeSection.append("\tFLD [").append(condTemp).append("]\n");
        codeSection.append("\tFTST\n");
        codeSection.append("\tFSTSW AX\n");
        codeSection.append("\tSAHF\n");
        codeSection.append("\tJE ").append(labelEnd).append("\n");

        genStatement(bodyIdx);

        codeSection.append("\tjmp ").append(labelWhile).append("\n");
        codeSection.append(labelEnd).append(":\n");
    }

    /** var = expr */
    private static void genAssign(int leftIdx, int rightIdx) {
        codeSection.append("\n; --- Asignación ---\n");

        Nodo varNode = GestorNodos.obtenerNodo(leftIdx);
        if (varNode == null) return; // error
        String varName = varNode.getValor();

        String exprTemp = genExpr(rightIdx);

        codeSection.append("\tFLD  [").append(exprTemp).append("]\n");
        codeSection.append("\tFSTP [").append(varName).append("]\n");
    }

    /**
     * Genera código (postorden) para una expresión y retorna
     * el nombre de un temporal donde está su resultado final (float).
     */
    private static String genExpr(int index) {
        if (index == 0) {
            String tmp = newTemp();
            codeSection.append("; genExpr(0)->0.0\n");
            codeSection.append("\tFLDZ\n");
            codeSection.append("\tFSTP [").append(tmp).append("]\n");
            return tmp;
        }
        Nodo nodo = GestorNodos.obtenerNodo(index);
        if (nodo == null) {
            String tmp = newTemp();
            codeSection.append("; nodo null->0.0\n");
            codeSection.append("\tFLDZ\n");
            codeSection.append("\tFSTP [").append(tmp).append("]\n");
            return tmp;
        }

        String val = nodo.getValor();
        int leftIdx  = getIndex(nodo.getIzquierdo());
        int rightIdx = getIndex(nodo.getDerecho());

        switch(val) {
            case "+":
            case "-":
            case "*":
            case "/":
                return genBinaryFloatOp(val, leftIdx, rightIdx);
            case "%":
                return genModOp(leftIdx,rightIdx);

            case "==":
            case "!=":
            case "<":
            case ">":
            case "<=":
            case ">=":
                return genComparison(val, leftIdx, rightIdx);

            default:
                return genLiteralOrVar(val);
        }
    }


    /**
     * Genera código assembler para el modulo: left % right
     */
    private static String genModOp(int leftIdx, int rightIdx) {
        codeSection.append("\n; --- Modulo (left % right) ---\n");
        // 1) Compilar subexpresiones
        String leftTemp = genExpr(leftIdx);
        String rightTemp = genExpr(rightIdx);

        // 2) Cargar left y right en la FPU, hacer division
        // resultDiv = newTemp();
        String resultDiv = newTemp(); // guardará left/right
        codeSection.append("\t;  Div = left / right\n");
        codeSection.append("\tFLD [").append(rightTemp).append("]\n");
        codeSection.append("\tFLD [").append(leftTemp).append("]\n");
        codeSection.append("\tFDIV ST0, ST1\n");       // ST0 = left/right
        codeSection.append("\tFSTP [").append(resultDiv).append("]\n");
        codeSection.append("\tFSTP ST0\n");

        // 3) Convertir resultDiv a entero => floor
        // Usamos FILD / FISTP => podemos hacer:
        //   mov eax, resultDiv (?), es un float => hay que re-cargar la FPU
        String tempFloor = newTemp(); // aquí guardamos floor
        codeSection.append("\t; Convert float->int => floor\n");
        // Cargamos en FPU:
        codeSection.append("\tFLD [").append(resultDiv).append("]\n");
        // Redondeamos/truncamos => FIST ?
        // Dependiendo del modo de la FPU, FSQRT, FRNDINT, etc.
        // Simplifiquemos con "FISTP"
        // Debemos tener un .data "intTemp DW ?" => en 16 bits. O "DD" si 32 bits
        // Por simplicidad, definimos un "int" temporal:
        String intSlot = defineIntSlot(); // crea, p.ej. "@int1 DD 0"
        codeSection.append("\tFISTP [").append(intSlot).append("]\n");

        // 4) Convertimos ese entero de vuelta a float => FILD
        String floorFloat = newTemp(); // guardará float version de floor
        codeSection.append("\tFLD  [").append(intSlot).append("]\n"); // FILD si intSlot es int
        codeSection.append("\tFSTP [").append(floorFloat).append("]\n");

        // 5) Multiplicar floorFloat * right => mulTemp
        String mulTemp = newTemp();
        codeSection.append("\n; mulTemp = floorDiv * right\n");
        codeSection.append("\tFLD [").append(rightTemp).append("]\n");
        codeSection.append("\tFLD [").append(floorFloat).append("]\n");
        codeSection.append("\tFMUL ST0, ST1\n");
        codeSection.append("\tFSTP [").append(mulTemp).append("]\n");
        codeSection.append("\tFSTP ST0\n");

        // 6) mod = left - mulTemp
        String resultMod = newTemp();
        codeSection.append("\n; mod = left - mulTemp\n");
        codeSection.append("\tFLD [").append(leftTemp).append("]\n");
        codeSection.append("\tFSUB [").append(mulTemp).append("]\n");
        codeSection.append("\tFSTP [").append(resultMod).append("]\n");

        return resultMod;
    }



    /** guarda literal o variable en un nuevo temporal. */
    private static String genLiteralOrVar(String val) {
        String tmp = newTemp();

        if (esNumero(val)) {
            // es un literal => defineLiteral
            String litName = defineLiteral(val);
            codeSection.append("\n; cargar literal ").append(val).append("\n");
            codeSection.append("\tFLD [").append(litName).append("]\n");
        } else {
            // es variable
            codeSection.append("\n; cargar variable ").append(val).append("\n");
            codeSection.append("\tFLD [").append(val).append("]\n");
        }
        codeSection.append("\tFSTP [").append(tmp).append("]\n");
        return tmp;
    }

    /** Operación binaria float: +, -, *, / */
    private static String genBinaryFloatOp(String op, int leftIdx, int rightIdx) {
        String leftTemp = genExpr(leftIdx);
        String rightTemp = genExpr(rightIdx);

        // resultado
        String result = newTemp();
        codeSection.append("\n; --- BinOp ").append(op).append(" ---\n");
        codeSection.append("\tFLD [").append(rightTemp).append("]\n");
        codeSection.append("\tFLD [").append(leftTemp).append("]\n");

        switch(op) {
            case "+":
                codeSection.append("\tFADD ST0, ST1\n");
                break;
            case "-":
                codeSection.append("\tFSUB ST0, ST1\n");
                break;
            case "*":
                codeSection.append("\tFMUL ST0, ST1\n");
                break;
            case "/":
                codeSection.append("\tFDIV ST0, ST1\n");
                break;
        }
        codeSection.append("\tFSTP [").append(result).append("]\n\n");
        return result;
    }


    private static String genComparison(String op, int leftIdx, int rightIdx) {
        String leftTemp = genExpr(leftIdx);
        String rightTemp = genExpr(rightIdx);
        String result = newTemp();

        codeSection.append("\n; --- Comparacion ").append(op).append(" ---\n");
        codeSection.append("\tFLD [").append(leftTemp).append("]\n");
        codeSection.append("\tFSUB [").append(rightTemp).append("]\n");

        codeSection.append("\tFTST\n");
        codeSection.append("\tFSTSW AX\n");
        codeSection.append("\tSAHF\n");

        String labelTrue = newLabel("CMPTRUE");
        String labelEnd  = newLabel("CMPEND");

        switch(op) {
            case "==":
                codeSection.append("\tJNE ").append(labelEnd).append("\n");
                break;
            case "!=":
                codeSection.append("\tJE  ").append(labelEnd).append("\n");
                break;
            case "<":
                codeSection.append("\tJGE ").append(labelEnd).append("\n");
                break;
            case ">":
                codeSection.append("\tJLE ").append(labelEnd).append("\n");
                break;
            case "<=":
                codeSection.append("\tJG  ").append(labelEnd).append("\n");
                break;
            case ">=":
                codeSection.append("\tJL  ").append(labelEnd).append("\n");
                break;
        }


        codeSection.append(labelTrue).append(":\n");
        codeSection.append("\tFLD1\n");
        codeSection.append("\tJMP ").append(labelEnd).append("\n");

        codeSection.append(labelEnd).append(":\n");
        // descartar la resta
        codeSection.append("\tFSTP ST0\n");
        // st0=0
        codeSection.append("\tFLDZ\n");
        codeSection.append("\tFSTP [").append(result).append("]\n\n");
        return result;
    }

    /** Crea un nombre de temporal en .data */
    private static String newTemp() {
        tempCount++;
        String tmpName = "@tmp" + tempCount;
        if (!declaredTemps.containsKey(tmpName)) {
            dataSection.append(tmpName).append(" DD 0.0\n");
            declaredTemps.put(tmpName, true);
        }
        return tmpName;
    }

    /** Crea un literal en .data  */
    private static String defineLiteral(String val) {
        tempCount++;
        String litName = "@lit" + tempCount;
        // Declarar en la sección de datos
        if (!declaredTemps.containsKey(litName)) {
            dataSection.append(litName).append(" DD ").append(val).append("\n");
            declaredTemps.put(litName, true);
        }
        return litName;
    }

    /** Genera un label único */
    private static String newLabel(String base) {
        labelCount++;
        return base + labelCount;
    }


    private static int getIndex(Nodo n) {
        if (n == null) return 0;
        return n.getIndice();
    }

    /** Checkea si la cadena es un número (int o float) */
    private static boolean esNumero(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
