package com.avianto.back;

import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class DataRepository {
  @PersistenceContext private EntityManager em;
  public <T> T get(Class<T> type, UUID id) { T value=em.find(type,id); if(value==null) throw new NotFoundException(type.getSimpleName()+" inexistente"); return value; }
  public <T> T save(T entity) { return em.merge(entity); }
  public void persist(Object entity) { em.persist(entity); }
  public <T> List<T> list(String jpql, Class<T> type, Map<String,?> params, int page, int size) { TypedQuery<T> q=em.createQuery(jpql,type); params.forEach(q::setParameter); return q.setFirstResult(page*size).setMaxResults(size).getResultList(); }
  public long count(String jpql, Map<String,?> params) { TypedQuery<Long> q=em.createQuery(jpql,Long.class); params.forEach(q::setParameter); return q.getSingleResult(); }
  public <T> List<T> all(String jpql, Class<T> type, Map<String,?> params) { TypedQuery<T> q=em.createQuery(jpql,type); params.forEach(q::setParameter); return q.getResultList(); }
  public <T> T one(String jpql, Class<T> type, Map<String,?> params) { try { TypedQuery<T> query=em.createQuery(jpql,type); params.forEach(query::setParameter); return query.setMaxResults(1).getResultStream().findFirst().orElse(null); } catch(NoResultException e) { return null; } }
}
