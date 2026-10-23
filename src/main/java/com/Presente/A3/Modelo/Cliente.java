package com.Presente.A3.Modelo;

public class Cliente {
 private Long id;
 private String nome;
 private String email;
 private int telefone;
 
 // Construtor
public Cliente(Long id, String nome, String email, int telefone) {
     this.id = id;
     this.nome = nome;
     this.email = email;
     this.telefone = telefone;
 }

 // Getters e Setters
 public Long getId() {
     return id;
 }

 public void setId(Long id) {
     this.id = id;
 }

 public String getNome() {
     return nome;
 }

 public void setNome(String nome) {
     this.nome = nome;
 }

 public String getEmail() {
     return email;
 }

 public void setEmail(String email) {
     this.email = email;
 }

 public int getTelefone() {
     return telefone;
 }

 public void setTelefone(int telefone) {
     this.telefone = telefone;
 
 }
   
   // Construtor vazio (necessário para o Spring Boot)
            public Cliente() {
 }
}
