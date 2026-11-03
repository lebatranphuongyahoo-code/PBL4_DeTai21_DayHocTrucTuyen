package com.pbl4.web;

import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Local operator access only. No public endpoint can grant itself privileged roles. */
@Component
public class OperatorCommand implements ApplicationRunner {
    private final Database db;
    public OperatorCommand(Database db){this.db=db;}
    @Override public void run(ApplicationArguments args)throws Exception {
        if(!args.containsOption("grant-teacher"))return;
        var names=args.getOptionValues("grant-teacher");
        if(names==null||names.size()!=1||names.getFirst().isBlank())throw new IllegalArgumentException("Use --grant-teacher=username");
        db.transaction(c->{
            try(var s=c.prepareStatement("UPDATE accounts SET role='TEACHER' WHERE username=? AND role='STUDENT' AND status='ACTIVE'")){
                s.setString(1,names.getFirst().strip());
                if(s.executeUpdate()!=1)throw new IllegalArgumentException("Active student not found; no account was changed.");
            }return null;
        });
        System.out.println("Teacher privilege granted by local operator. Restart browser login to refresh role.");
    }
}
