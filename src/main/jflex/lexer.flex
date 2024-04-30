package lyc.compiler;

import java_cup.runtime.Symbol;
import lyc.compiler.ParserSym;
import lyc.compiler.model.*;
import static lyc.compiler.constants.Constants.*;
import java.lang.Math;
import java.util.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;


%%

%public
%class Lexer
%unicode
%cup
%line
%column
%throws CompilerException
%eofval{
  return symbol(ParserSym.EOF);
%eofval}


%{
   int IDENTIFIER_RANGE = 40;
    int INTEGER_RANGE = (int) (Math.pow(2, 16)-1);
    float FLOAT_RANGE = (float) (Math.pow(2, 32)-1);
    int STRING_RANGE = 40;




  private Symbol symbol(int type) {
    return new Symbol(type, yyline, yycolumn);
  }
  private Symbol symbol(int type, Object value) {
    return new Symbol(type, yyline, yycolumn, value);
  }
%}


LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]
Identation =  [ \t\f]

Space = " "
Plus = "+"
Mult = "*"
Sub = "-"
Div = "/"
Assig = ":="
OpenBracket = "("
CloseBracket = ")"
QuotationMark = \"
AllowedSymbols = {Plus} | {Mult} | {Sub} | {Div} | {Assig}
Letter = [a-zA-Z]
Digit = [0-9]
Coma = ","
PyC = ";"
D_P = ":"
C_A = "["
C_C = "]"
LL_A = "{"
LL_C = "}"
Menor_Ig = "<="
Menor = "<"
Mayor_Ig = ">="
Mayor = ">"
Igual = "=="
And = "and"
Or = "or"
Not = "not"
Distinto = "!="
Si = "Si"
Mientras = "Mientras"
Sino = "Sino"
leer = "leer"
escribir = "escribir"
Int = "Int"
Float = "Float"
String = "String"
init = "init"
busco_y_reemplazo = "busco_y_reemplazo"

WhiteSpace = {LineTerminator} | {Identation}
Identifier = {Letter} ({Letter}|{Digit})*
IntegerConstant = {Digit}+
RealConstant =  ({Digit})*"."({Digit})*
StringConstant = {QuotationMark} ({Letter}|{Digit}|{Space}|{AllowedSymbols})* {QuotationMark}
Comment = "*-"({Letter}|{Digit}|{Space}|{AllowedSymbols})* "-*"

%%


/* keywords */

<YYINITIAL> {
/* Comments */
  {Comment}                                { /* ignore */ }

 /* reserverd words */
  {Si}                                      { return symbol(ParserSym.SI); }
  {Mientras}                                { return symbol(ParserSym.MIENTRAS); }
  {Sino}                                    { return symbol(ParserSym.SINO); }
  {leer}                                    { return symbol(ParserSym.LEER); }
  {escribir}                                { return symbol(ParserSym.ESCRIBIR); }
  {busco_y_reemplazo}                       { return symbol(ParserSym.BUSCO_Y_REEMPLAZO); }
  {Int}                                     { return symbol(ParserSym.INT); }
  {Float}                                   { return symbol(ParserSym.FLOAT); }
  {String}                                  { return symbol(ParserSym.STRING); }
  {init}                                    { return symbol(ParserSym.INIT); }
  /* identifiers */
   {Identifier}                             {
                                               String id = new String(yytext());
                                               int length = id.length();
                                               if(length > IDENTIFIER_RANGE )
                                               {
                                                 throw new Error("El identificador [" + yytext() + "] excede el limite de caracteres.");
                                               }
                                               return symbol(ParserSym.IDENTIFIER, yytext()); }
  /* Constants */
  {IntegerConstant}                        { Integer constInt = Integer.parseInt(yytext());
                                              if(Math.abs(constInt) > INTEGER_RANGE )
                                              {
                                                throw new Error("La constante [" + yytext() + "] esta fuera del rango de los enteros.");
                                              }
                                              return symbol(ParserSym.INTEGER_CONSTANT,yytext()); }
   {RealConstant}                            { Double constFloat = Double.parseDouble(yytext());
                                                if (Math.abs(constFloat) > FLOAT_RANGE)
                                                {
                                                 throw new Error("La constante [" + yytext() + "] esta fuera del limite de los flotantes.");
                                                }
                                                return symbol(ParserSym.REAL_CONSTANT, yytext()); }
    {StringConstant}                          { String constString = new String(yytext());
                                                // Se borran las dos comillas.
                                                if (constString.length() -2 > STRING_RANGE)
                                                {
                                                  throw new Error("La constante [" + yytext() + "] excede el largo permitido para un string.");
                                                }
                                                 return symbol(ParserSym.STRING_CONSTANT, yytext()); }



  /* operators */
  {Plus}                                    { return symbol(ParserSym.PLUS); }
  {Sub}                                     { return symbol(ParserSym.SUB); }
  {Mult}                                    { return symbol(ParserSym.MULT); }
  {Div}                                     { return symbol(ParserSym.DIV); }
  {Assig}                                   { return symbol(ParserSym.ASSIG); }
  {OpenBracket}                             { return symbol(ParserSym.OPEN_BRACKET); }
  {CloseBracket}                            { return symbol(ParserSym.CLOSE_BRACKET); }
   {Coma}                                    { return symbol(ParserSym.COMA); }
    {PyC}                                     { return symbol(ParserSym.PUNTO_COMA); }
    {D_P}                                     { return symbol(ParserSym.DOS_PUNTOS); }
    {C_A}                                     { return symbol(ParserSym.CORCH_ABRE); }
    {C_C}                                     { return symbol(ParserSym.CORCH_CIERRA); }
    {LL_A}                                    { return symbol(ParserSym.LLAVE_ABRE); }
    {LL_C}                                    { return symbol(ParserSym.LLAVE_CIERRA); }
    {Menor_Ig}                                { return symbol(ParserSym.MENOR_IGUAL); }
    {Menor}                                   { return symbol(ParserSym.MENOR); }
     {Mayor_Ig}                                { return symbol(ParserSym.MAYOR_IGUAL); }
      {Mayor}                                   { return symbol(ParserSym.MAYOR); }
      {Igual}                                   { return symbol(ParserSym.IGUAL); }
      {And}                                     { return symbol(ParserSym.AND); }
      {Or}                                      { return symbol(ParserSym.OR); }
      {Not}                                     { return symbol(ParserSym.NOT); }
      {Distinto}                                { return symbol(ParserSym.DISTINTO); }

  /* whitespace */
  {WhiteSpace}                   { /* ignore */ }
}


/* error fallback */
[^]                              { throw new UnknownCharacterException(yytext()); }
