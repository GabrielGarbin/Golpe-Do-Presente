package com.Presente.A3.Service;
import com.Presente.A3.Modelo.Cliente;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ClienteService {

    private static final List<Cliente> clientes = new ArrayList<>();
    private static final AtomicLong counter = new AtomicLong();

    public List<Cliente> findAll() {
        return clientes;
    }

    public Optional<Cliente> findById(Long id) {
        return clientes.stream().filter(c -> Objects.equals(getPropertyValue(c, "id"), id)).findFirst();
    }

    public Cliente save(Cliente cliente) {
        setPropertyValue(cliente, "id", counter.incrementAndGet());
        clientes.add(cliente);
        return cliente;
    }

    public Optional<Cliente> update(Long id, Cliente clienteAtualizado) {
        Optional<Cliente> clienteExistente = findById(id);
        if (clienteExistente.isPresent()) {
            Cliente c = clienteExistente.get();
            setPropertyValue(c, "nome", getPropertyValue(clienteAtualizado, "nome"));
            setPropertyValue(c, "email", getPropertyValue(clienteAtualizado, "email"));
            setPropertyValue(c, "telefone", getPropertyValue(clienteAtualizado, "telefone"));
            return Optional.of(c);
        }
        return Optional.empty();
    }

    public boolean deleteById(Long id) {
        return clientes.removeIf(c -> Objects.equals(getPropertyValue(c, "id"), id));
    }

    private static Object getPropertyValue(Cliente cliente, String propertyName) {
        try {
            Field field = Cliente.class.getDeclaredField(propertyName);
            field.setAccessible(true);
            return field.get(cliente);
        } catch (NoSuchFieldException ignored) {
            try {
                String methodName = "get" + capitalize(propertyName);
                Method method = Cliente.class.getMethod(methodName);
                return method.invoke(cliente);
            } catch (Exception e) {
                throw new IllegalArgumentException("Propriedade '" + propertyName + "' não encontrada em Cliente.", e);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao acessar a propriedade '" + propertyName + "'.", e);
        }
    }

    private static void setPropertyValue(Cliente cliente, String propertyName, Object value) {
        try {
            Field field = Cliente.class.getDeclaredField(propertyName);
            field.setAccessible(true);
            field.set(cliente, value);
            return;
        } catch (NoSuchFieldException ignored) {
            try {
                String methodName = "set" + capitalize(propertyName);
                Method method = Cliente.class.getMethod(methodName, value.getClass());
                method.invoke(cliente, value);
                return;
            } catch (Exception e) {
                throw new IllegalArgumentException("Propriedade '" + propertyName + "' não encontrada em Cliente.", e);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao definir a propriedade '" + propertyName + "'.", e);
        }
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}

