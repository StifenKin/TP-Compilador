package lyc.compiler.tree;

public class Nodo {
    // Contador estático para asignar un ID único a cada nodo
    private static int NODE_COUNTER = 0;

    // Atributos principales
    private final int indice;     // ID único del nodo
    private final String valor;   // Puede ser operador (+, -, /, etc.), o un literal/variable
    private final Nodo izquierdo;
    private final Nodo derecho;

    /**
     * Constructor para crear nodos internos (operadores),
     * con hijo izquierdo y derecho.
     */
    public Nodo(String valor, Nodo izquierdo, Nodo derecho) {
        this.indice = ++NODE_COUNTER;
        this.valor = valor;
        this.izquierdo = izquierdo;
        this.derecho = derecho;
    }


    /**
     * Constructor para crear nodos internos (operadores),
     * con hijo izquierdo.
     */
    public Nodo(String valor, Nodo izquierdo) {
        this.indice = ++NODE_COUNTER;
        this.valor = valor;
        this.izquierdo = izquierdo;
        this.derecho = null;
    }


    /**
     * Constructor para crear nodos hoja (ej: constantes numéricas o variables).
     * En este caso, no hay hijos.
     */
    public Nodo(String valor) {
        this.indice = ++NODE_COUNTER;
        this.valor = valor;
        this.izquierdo = null;
        this.derecho = null;
    }

    // Getters
    public int getIndice() { return indice; }
    public String getValor() { return valor; }
    public Nodo getIzquierdo() { return izquierdo; }
    public Nodo getDerecho() { return derecho; }

    // Representación en texto para depuración
    @Override
    public String toString() {
        return "Nodo{indice=" + indice + ", valor='" + valor + "'}";
    }
}
