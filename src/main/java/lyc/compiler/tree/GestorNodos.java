package lyc.compiler.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GestorNodos {
    // Almacena todos los nodos. Clave = indice del nodo, Valor = el propio nodo
    private static final HashMap<Integer, Nodo> nodos = new HashMap<>();

    /**
     * Crear un nodo interno: se reciben 2 índices de hijos ya creados.
     */
    public static int crearNodo(String valor, int indiceIzq, int indiceDer) {
        Nodo izquierdo = nodos.get(indiceIzq);
        Nodo derecho = nodos.get(indiceDer);
        Nodo nuevoNodo = new Nodo(valor, izquierdo, derecho);
        nodos.put(nuevoNodo.getIndice(), nuevoNodo);
        return nuevoNodo.getIndice();
    }


    /**
     * Crear un nodo interno: se reciben 1 índices de hijo ya creado.
     */
    public static int crearNodo(String valor, int indiceIzq) {
        Nodo izquierdo = nodos.get(indiceIzq);
        Nodo nuevoNodo = new Nodo(valor, izquierdo);
        nodos.put(nuevoNodo.getIndice(), nuevoNodo);
        return nuevoNodo.getIndice();
    }


    /*
      Crear un nodo interno: se reciben 3 índices de hijos ya creados.

    public static int crearNodo(int indicePadre, int indiceIzq, int indiceDer) {
        Nodo padre = nodos.get(indicePadre);
        Nodo izquierdo = nodos.get(indiceIzq);
        Nodo derecho = nodos.get(indiceDer);
        Nodo nuevoNodo = new Nodo(padre, izquierdo, derecho);
        nodos.put(nuevoNodo.getIndice(), nuevoNodo);
        return nuevoNodo.getIndice();
    }
     */

    /**
     * Crear un nodo hoja: no tiene hijos.
     */
    public static int crearNodo(String valor) {
        Nodo nuevoNodo = new Nodo(valor);
        nodos.put(nuevoNodo.getIndice(), nuevoNodo);
        return nuevoNodo.getIndice();
    }

    /**
     * Retorna el nodo a partir de su índice.
     */
    public static Nodo obtenerNodo(int indice) {
        return nodos.get(indice);
    }

    /**
     * Imprime el árbol (o subárbol) con raíz en 'indice' en formato visual.
     * Recorrido in-order visual (derecha -> nodo -> izquierda).
     */
    public static void imprimirArbol(int indice) {
        Nodo raiz = nodos.get(indice);
        imprimirRecursivo(raiz, 0);
    }

    private static void imprimirRecursivo(Nodo nodo, int nivel) {
        if (nodo == null) return;

        // 1. Imprimir primero el hijo derecho
        imprimirRecursivo(nodo.getDerecho(), nivel + 1);

        // 2. Imprimir este nodo con la indentación necesaria
        String indent = "    ".repeat(nivel);
        System.out.println(indent + nodo.getValor() + " (" + nodo.getIndice() + ")");

        // 3. Imprimir el hijo izquierdo
        imprimirRecursivo(nodo.getIzquierdo(), nivel + 1);
    }

    /**
 * Dado el índice de un nodo raíz de una lista (conectada por ","), 
 * retorna una lista con los índices de todos los elementos en orden.
 */
    public static List<Integer> obtenerHijos(int nodoIdx) {
        List<Integer> hijos = new ArrayList<>();
        recorrer(nodoIdx, hijos);
        return hijos;
    }

    private static void recorrer(int idx, List<Integer> hijos) {
        Nodo n = nodos.get(idx);
        if (",".equals(n.getValor())) {
            if (n.getIzquierdo() != null) recorrer(n.getIzquierdo().getIndice(), hijos);
            if (n.getDerecho() != null) recorrer(n.getDerecho().getIndice(), hijos);
        } else {
            hijos.add(idx);
        }
    }
}
