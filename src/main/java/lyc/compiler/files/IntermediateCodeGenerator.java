package lyc.compiler.files;

import lyc.compiler.tree.*;

import java.io.*;


import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;


public class IntermediateCodeGenerator implements FileGenerator {


    public static void generarDot(String filePath, Nodo nodoRoot) throws IOException {
        File file = new File(filePath);

        // Imprime en consola la ruta absoluta para verificar dónde se va a crear el archivo
        System.out.println("Generando archivo DOT en: " + file.getAbsolutePath());

        try (FileWriter writer = new FileWriter(file)) {
            writer.write("digraph AST {\n");
            writer.write("    node [shape=record];\n");

            // Invocamos un metodo recursivo que escribe los nodos y las aristas
            escribirNodo(writer, nodoRoot);

            writer.write("}\n");
        }
    }

    /**
     * Escribe recursivamente un nodo y sus hijos en el archivo DOT.
     *
     * @param writer  escritor hacia el archivo
     * @param nodo    nodo actual del árbol
     * @throws IOException si falla la escritura del archivo
     */
    public static void escribirNodo(FileWriter writer, Nodo nodo) throws IOException {
        if (nodo == null) {
            return; // No hay nada que escribir
        }

        // 1) Construimos la etiqueta (por ejemplo: "valor (indice)")
        int index = nodo.getIndice();
        // Antes de componer el label, escapamos caracteres que Graphviz pueda interpretar mal
        String rawLabel = nodo.getValor() + " (" + index + ")";
        // Escapamos comillas, < y >
        String label = rawLabel
                .replace("\"", "\\\"")
                .replace("<", "\\<")
                .replace(">", "\\>");

        // 2) Definimos el nodo en el archivo DOT
        //    Lo identificamos por su 'index', y le damos una etiqueta
        writer.write("    n" + index + " [label=\"" + label + "\"];\n");

        // 3) Enlazamos el hijo izquierdo, si existe
        Nodo izq = nodo.getIzquierdo();
        if (izq != null) {
            writer.write("    n" + index + " -> n" + izq.getIndice() + ";\n");
            escribirNodo(writer, izq); // Llamada recursiva
        }

        // 4) Enlazamos el hijo derecho, si existe
        Nodo der = nodo.getDerecho();
        if (der != null) {
            writer.write("    n" + index + " -> n" + der.getIndice() + ";\n");
            escribirNodo(writer, der);
        }
    }




    @Override
    public void generate(FileWriter fileWriter) throws IOException {
        fileWriter.write("+------------------------------- ARBOL -------------------------------+\n");

        // Supongamos que tienes una clase ASTManager que almacena la raíz del AST.
        Integer root = ASTManager.getRoot(); // Debes implementar ASTManager para guardar la raíz del AST.

        if (root != null) {
            GestorNodos.imprimirArbol(root);
            //printTree(root, fileWriter, 0);
        } else {
            fileWriter.write("Árbol vacío.\n");
        }
    }


}