package com.eka.middleware.sqlite.impl;


import com.eka.middleware.sqlite.connection.ConnectionManager;
import com.eka.middleware.sqlite.entity.Group;
import com.eka.middleware.sqlite.entity.User;
import com.eka.middleware.template.SystemException;


import java.sql.*;
import java.util.*;

import static com.eka.middleware.auth.UserProfileManager.isUserExist;
import static com.eka.middleware.auth.UserProfileManager.usersMap;

public class UserRepoImpl {

    public static Map<String, Object> getUsers() throws SystemException {
        Map<String, Object> usersMap = new HashMap<>();

        try (Connection conn = ConnectionManager.getConnection()) {
            String userSql = "SELECT u.* FROM users u";
            try (PreparedStatement userStatement = conn.prepareStatement(userSql)) {
                ResultSet userResultSet = userStatement.executeQuery();

                while (userResultSet.next()) {
                    String userId = userResultSet.getString("user_id");
                    int tenantId = userResultSet.getInt("tenant_id");

                    String passwordHash = userResultSet.getString("password");
                    String name = userResultSet.getString("name");
                    String email = userResultSet.getString("email");
                    String status = userResultSet.getString("status");

                    String tenantSql = "SELECT t.name FROM tenant t WHERE t.tenant_id = ?";
                    String groupSql = "SELECT g.name FROM \"groups\" g " +
                            "JOIN user_group_mapping ug ON g.group_id = ug.group_id " +
                            "WHERE ug.user_id = ?";


                    try (PreparedStatement tenantStatement = conn.prepareStatement(tenantSql);
                         PreparedStatement groupStatement = conn.prepareStatement(groupSql)) {

                        tenantStatement.setInt(1, tenantId); // Set the tenant_id parameter
                        ResultSet tenantResultSet = tenantStatement.executeQuery();

                        String tenantName = null;
                        if (tenantResultSet.next()) {
                            tenantName = tenantResultSet.getString("name");
                        }

                        groupStatement.setString(1, userId);
                        ResultSet groupResultSet = groupStatement.executeQuery();

                        List<String> groupNames = new ArrayList<>();
                        while (groupResultSet.next()) {
                            String groupName = groupResultSet.getString("name");
                            groupNames.add(groupName);
                        }

                        Map<String, Object> profile = new HashMap<>();
                        profile.put("name", name);
                        profile.put("groups", groupNames);
                        profile.put("email", email);
                        profile.put("tenant", tenantName);

                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("password", passwordHash);
                        userMap.put("profile", profile);
                        userMap.put("status", status);

                        usersMap.put(email, userMap);
                    }
                }
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
        return usersMap;
    }

    public static void addUser(User user) throws SystemException {
        try (Connection conn = ConnectionManager.getConnection()) {
            if (isUserExist(user.getEmail())) {
                throw new SystemException("EKA_MWS_1002", new Exception("User already exists with email: " + user.getEmail()));
            }

            String sql = "INSERT INTO \"users\" (password, name, email, tenant_id, status, user_id) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, user.getPassword());
                statement.setString(2, user.getName());
                statement.setString(3, user.getEmail());
                statement.setInt(4, user.getTenant());
                statement.setString(5, user.getStatus());
                statement.setString(6, user.getUser_id());

                statement.executeUpdate();

                ResultSet generatedKeys = statement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int userId = generatedKeys.getInt(1);
                    String user_id = user.getUser_id();
                    addGroupsForUser(conn, user_id, user.getGroups());
                } else {
                    throw new SystemException("EKA_MWS_1002", new Exception("Failed to retrieve user_id after insert"));
                }
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }



    public static void updateUser(String email, User user) throws SystemException {
        try (Connection conn = ConnectionManager.getConnection()) {
            String sql = "UPDATE \"users\" SET password = ?, name = ?, email = ?, tenant_id = ?, status = ? WHERE email = ?";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, user.getPassword());
                statement.setString(2, user.getName());
                statement.setString(3, user.getEmail());
                statement.setInt(4, 1);
                statement.setString(5, user.getStatus());
                statement.setString(6, email);
                statement.executeUpdate();
            }

            String userId = getUserIdByEmail(conn, user.getEmail());
            updateGroupsForUser(conn, userId, user.getGroups());
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }

    public static void deleteUser(String email) throws SystemException {
        try (Connection conn = ConnectionManager.getConnection()) {
            String userId = getUserIdByEmail(conn, email);
            deleteGroupsForUser(conn, userId);

            String sql = "DELETE FROM \"users\" WHERE email = ?";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, email);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }


    private static String getUserIdByEmail(Connection conn, String email) throws SQLException {
        String userIdSql = "SELECT user_id FROM \"users\" WHERE email = ?";
        try (PreparedStatement userIdStatement = conn.prepareStatement(userIdSql)) {
            userIdStatement.setString(1, email);
            try (ResultSet userIdResultSet = userIdStatement.executeQuery()) {
                return userIdResultSet.next() ? userIdResultSet.getString("user_id") : null;
            }
        }
    }

    private static void addGroupsForUser(Connection conn, String userId, List<Group> groups) throws SQLException {
        String insertGroupSql = "INSERT INTO user_group_mapping (user_id, group_id) VALUES (?, ?)";
        try (PreparedStatement insertGroupStatement = conn.prepareStatement(insertGroupSql)) {
            for (Group group : groups) {
                int groupId = getGroupIdByName(conn, group.getGroupName());
                if (groupId != -1) {
                    insertGroupStatement.setString(1, userId);
                    insertGroupStatement.setInt(2, groupId);
                    insertGroupStatement.executeUpdate();
                } else {
                    System.out.println("Group not found: " + group.getGroupName());
                }
            }
        }
    }

    private static void updateGroupsForUser(Connection conn, String userId, List<Group> groups) throws SQLException {
        String deleteGroupSql = "DELETE FROM user_group_mapping WHERE user_id = ?";
        try (PreparedStatement deleteGroupStatement = conn.prepareStatement(deleteGroupSql)) {
            deleteGroupStatement.setString(1, userId);
            deleteGroupStatement.executeUpdate();
        }

        addGroupsForUser(conn, userId, groups);
    }

    private static void deleteGroupsForUser(Connection conn, String userId) throws SQLException {
        String deleteGroupSql = "DELETE FROM user_group_mapping WHERE user_id = ?";
        try (PreparedStatement deleteGroupStatement = conn.prepareStatement(deleteGroupSql)) {
            deleteGroupStatement.setString(1, userId);
            deleteGroupStatement.executeUpdate();
        }
    }

    private static int getGroupIdByName(Connection conn, String groupName) throws SQLException {
        String groupIdSql = "SELECT group_id FROM \"groups\" WHERE name = ?";
        try (PreparedStatement groupIdStatement = conn.prepareStatement(groupIdSql)) {
            groupIdStatement.setString(1, groupName);
            try (ResultSet groupIdResultSet = groupIdStatement.executeQuery()) {
                if (groupIdResultSet.next()) {
                    return groupIdResultSet.getInt("group_id");
                } else {
                    return -1; // Group not found
                }
            }
        }
    }
}






