package com.Presente.A3.Service;

import com.Presente.A3.Modelo.Cliente;
import com.Presente.A3.Repositorio.ClienteRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** CRUD de clientes, persistido na tabela {@code clientes} do Supabase. */
@Service
public class ClienteService {

    private final ClienteRepository repositorio;

    public ClienteService(ClienteRepository repositorio) {
        this.repositorio = repositorio;
    }

    public List<Cliente> findAll() {
        return repositorio.findAll(Sort.by("id"));
    }

    public Optional<Cliente> findById(Long id) {
        return id == null ? Optional.empty() : repositorio.findById(id);
    }

    public long count() {
        return repositorio.count();
    }

    /** O id é sempre gerado pelo banco; um id vindo no JSON é ignorado. */
    @Transactional
    public Cliente save(Cliente cliente) {
        cliente.setId(null);
        return repositorio.save(cliente);
    }

    @Transactional
    public Optional<Cliente> update(Long id, Cliente clienteAtualizado) {
        return findById(id).map(c -> {
            c.setNome(clienteAtualizado.getNome());
            c.setEmail(clienteAtualizado.getEmail());
            c.setTelefone(clienteAtualizado.getTelefone());
            return repositorio.save(c);
        });
    }

    /** As denúncias feitas pelo cliente continuam no banco (cliente_id vira nulo). */
    @Transactional
    public boolean deleteById(Long id) {
        if (id == null || !repositorio.existsById(id)) {
            return false;
        }
        repositorio.deleteById(id);
        return true;
    }
}
