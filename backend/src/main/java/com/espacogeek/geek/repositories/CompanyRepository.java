package com.espacogeek.geek.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.espacogeek.geek.models.CompanyModel;

public interface CompanyRepository extends JpaRepository<CompanyModel, Integer>{

}
