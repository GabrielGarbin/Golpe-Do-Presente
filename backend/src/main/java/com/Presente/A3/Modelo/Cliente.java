package com.Presente.A3.Modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Pessoa cadastrada que pode fazer denúncias. Tabela {@code public.clientes} no Supabase. */
@Entity
@Table(name = "clientes")
public class Cliente {
 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 private Long id;
 private String nome;
 private String email;
 private String telefone;
 
 // Construtor
public Cliente(Long id, String nome, String email, String telefone) {
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

 public String getTelefone() {
     return telefone;
 }

 public void setTelefone(String telefone) {
     this.telefone = telefone;
 
 }
   
   // Construtor vazio (necessário para o Spring Boot)
            public Cliente() {
 }
}
