package com.pbl4.web;

import java.sql.SQLException;

final class TestAccounts {
    private TestAccounts() {}

    static Database.Account asTeacher(Database db,String username)throws SQLException {
        if(!db.setRole(username,"TEACHER"))throw new IllegalArgumentException("Teacher test account not found: "+username);
        return db.findAccount(username);
    }
}
