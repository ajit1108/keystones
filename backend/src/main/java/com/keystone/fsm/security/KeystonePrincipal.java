package com.keystone.fsm.security;

import com.keystone.fsm.domain.enums.Role;
import com.keystone.fsm.domain.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class KeystonePrincipal implements UserDetails {

    private final String id;
    private final String email;
    private final String passwordHash;
    private final String customerId;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;

    public KeystonePrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.customerId = user.getCustomerId();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        this.enabled = user.isEnabled();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    public Role getRole() {
        return Role.valueOf(authorities.iterator().next().getAuthority().replace("ROLE_", ""));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return enabled; }
}
